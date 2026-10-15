package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.adapter.in.webhook.ResendWebhooks;
import com.pijamaparty.store.ordering.application.ConfirmOrderPayment;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-18 (T-E3-10) through the back-office API, with the real security filter chain, Row Level Security and
 * PostgreSQL: a guest places its order through the checkout, the payment confirms it as the webhook does, and the
 * administrator lists, opens and changes it. The detail is the backoffice module's.
 */
@ApiTest
class OrderAdminApiTest {

    private static final String ORDERS = "/api/admin/orders";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final ConfirmOrderPayment confirmOrderPayment;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;
    private UUID pijama;

    OrderAdminApiTest(MockMvc mvc, ObjectMapper json, ConfirmOrderPayment confirmOrderPayment,
                      PlatformTransactionManager transactionManager, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.confirmOrderPayment = confirmOrderPayment;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** Pijamas at ARS 20,000, 5 in stock; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void setUp() {
        pijama = sellable();
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values ('SANTA_FE', 450000, "
            + "now())");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, email_event, email_outbox, "
            + "withdrawal_request, order_tracking_token, order_item, orders, cart_item, cart, stock_movement, "
            + "stock_level, product_image, "
            + "variant, product, category, shipping_zone_rate");
    }

    @Test
    void givenTheListOfOrders_whenFilteredByPaid_thenOnlyPaidOrdersAppear() throws Exception {
        Placed paid = placeOrder();
        pay(paid);
        Placed waiting = placeOrder();

        mvc.perform(get(ORDERS).param("status", "PAID").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].number", contains((int) paid.number())))
            .andExpect(jsonPath("$.items[0].status").value("PAID"))
            .andExpect(jsonPath("$.items[0].buyerName").value("Ana Pérez"))
            .andExpect(jsonPath("$.items[0].province").value("SANTA_FE"))
            .andExpect(jsonPath("$.items[0].totalCents").value(4_450_000))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(ORDERS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].number", contains((int) waiting.number(), (int) paid.number())));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenMovingItToShippedIsAttempted_thenItIsRejected() throws Exception {
        Placed placed = placeOrder();

        change(placed, """
            {"from":"AWAITING_PAYMENT","to":"SHIPPED","shipmentTrackingNumber":"CA123456789AR"}
            """)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_TRANSITION"));
        assertThat(statusOf(placed)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void givenAnOrderThatBecomesShipped_whenTheTrackingNumberIsSaved_thenTheBuyerSeesItOnItsTrackingLink()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        change(placed, "{\"from\":\"PAID\",\"to\":\"PREPARING\"}").andExpect(status().isOk());

        change(placed, """
            {"from":"PREPARING","to":"SHIPPED","shipmentTrackingNumber":" CA000000000AR "}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SHIPPED"));

        track(placed).andExpect(jsonPath("$.shipmentTrackingNumber").value("CA000000000AR"));
        assertThat(emails(placed)).isEqualTo(2);
    }

    @Test
    void givenAShippedOrder_whenItsTrackingNumberIsCorrected_thenTheBuyerSeesTheNewOneUntilItIsDelivered()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        change(placed, "{\"from\":\"PAID\",\"to\":\"PREPARING\"}").andExpect(status().isOk());
        change(placed, "{\"from\":\"PREPARING\",\"to\":\"SHIPPED\",\"shipmentTrackingNumber\":\"CA0\"}")
            .andExpect(status().isOk());

        correct(placed, "CA123456789AR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shipmentTrackingNumber").value("CA123456789AR"));
        track(placed).andExpect(jsonPath("$.shipmentTrackingNumber").value("CA123456789AR"));

        change(placed, "{\"from\":\"SHIPPED\",\"to\":\"DELIVERED\"}").andExpect(status().isOk());
        assertThat(owner.queryForObject("select delivered_at is not null from orders where number = ?",
            Boolean.class, placed.number())).isTrue();
        correct(placed, "XX")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("ORDER_NOT_SHIPPED"))
            .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    void givenAPaidOrder_whenCancelledPuttingTheStockBack_thenEveryLinesUnitsAreAvailableAgain() throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        assertThat(onHand()).isEqualTo(3);

        change(placed, "{\"from\":\"PAID\",\"to\":\"CANCELLED\",\"restock\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(onHand()).isEqualTo(5);
        assertThat(statusOf(placed)).isEqualTo("CANCELLED");
    }

    @Test
    void givenTwoCancellationsOfAPaidOrderAtOnce_whenProcessed_thenOnlyOneAppliesAndTheStockGoesBackOnce()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {
            Callable<Integer> cancel = () -> {
                start.await(10, TimeUnit.SECONDS);
                return change(placed, "{\"from\":\"PAID\",\"to\":\"CANCELLED\",\"restock\":true}")
                    .andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> answers = List.of(executor.submit(cancel), executor.submit(cancel));
            start.countDown();
            statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(onHand()).isEqualTo(5);
        assertThat(owner.queryForObject("select count(*) from stock_movement where type = 'RESTOCK'", Long.class))
            .isOne();
    }

    @Test
    void givenACancellationAndThePaymentAtOnce_whenProcessed_thenOneWinsAndTheOtherSeesItsResult() throws Exception {
        Placed placed = placeOrder();
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> cancel = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return change(placed, "{\"from\":\"AWAITING_PAYMENT\",\"to\":\"CANCELLED\"}")
                    .andReturn().getResponse().getStatus();
            });
            Future<?> payment = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                pay(placed);
                return null;
            });
            start.countDown();
            int cancelled = cancel.get(30, TimeUnit.SECONDS);
            payment.get(30, TimeUnit.SECONDS);

            if (cancelled == 200) {
                assertThat(statusOf(placed)).isEqualTo("CANCELLED");
                assertThat(owner.queryForObject("select paid_after_closing_at is not null from orders where id = ?",
                    Boolean.class, placed.id())).isTrue();
                assertThat(onHand()).isEqualTo(5);
            } else {
                assertThat(cancelled).isEqualTo(409);
                assertThat(statusOf(placed)).isEqualTo("PAID");
                assertThat(onHand()).isEqualTo(3);
            }
            assertThat(reserved()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void givenAPaidOrder_whenCancelledWithoutPuttingTheStockBack_thenTheStockDoesNotChange() throws Exception {
        Placed placed = placeOrder();
        pay(placed);

        change(placed, "{\"from\":\"PAID\",\"to\":\"CANCELLED\",\"restock\":false}").andExpect(status().isOk());

        assertThat(onHand()).isEqualTo(3);
    }

    @Test
    void givenAPaidOrder_whenCancelledWithoutDecidingAboutTheStock_thenItIsRefused() throws Exception {
        Placed placed = placeOrder();
        pay(placed);

        change(placed, "{\"from\":\"PAID\",\"to\":\"CANCELLED\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("RESTOCK_DECISION_REQUIRED"));
        change(placed, "{\"from\":\"PAID\",\"to\":\"PREPARING\",\"restock\":true}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("UNEXPECTED_FIELD"))
            .andExpect(jsonPath("$.field").value("restock"));
        change(placed, "{\"from\":\"PREPARING\",\"to\":\"SHIPPED\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("ORDER_STATUS_CHANGED"));
        assertThat(statusOf(placed)).isEqualTo("PAID");
    }

    @Test
    void givenAnOrderAwaitingPayment_whenCancelled_thenItsReservationIsReleased() throws Exception {
        Placed placed = placeOrder();
        assertThat(reserved()).isEqualTo(2);

        change(placed, "{\"from\":\"AWAITING_PAYMENT\",\"to\":\"CANCELLED\"}").andExpect(status().isOk());

        assertThat(reserved()).isZero();
        assertThat(onHand()).isEqualTo(5);
    }

    @Test
    void givenAnOrderThatChangedAfterTheAdministratorOpenedIt_whenChangedFromTheStatusSeen_thenItIsRejected()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);

        change(placed, "{\"from\":\"AWAITING_PAYMENT\",\"to\":\"CANCELLED\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("ORDER_STATUS_CHANGED"))
            .andExpect(jsonPath("$.currentStatus").value("PAID"));

        assertThat(statusOf(placed)).isEqualTo("PAID");
        assertThat(onHand()).isEqualTo(3);
    }

    @Test
    void givenAnOrderWhoseBuyersEmailFailed_whenTheAdministratorOpensTheDetail_thenAnAlertShowsTheBuyersPhone()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        owner.update("update email_outbox set status = 'FAILED' where type = 'ORDER_PAID_CUSTOMER'");

        mvc.perform(get(ORDERS + "/{id}", placed.id()).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(placed.number()))
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.nextStatuses", contains("PREPARING", "CANCELLED")))
            .andExpect(jsonPath("$.alerts", contains("BUYER_EMAIL_FAILED")))
            .andExpect(jsonPath("$.buyer.phone").value("3425551234"))
            .andExpect(jsonPath("$.buyer.nationalId").value("12345678"))
            .andExpect(jsonPath("$.address.city").value("Santa Fe"))
            .andExpect(jsonPath("$.lines[0].productName").value("Pijama"))
            .andExpect(jsonPath("$.lines[0].quantity").value(2))
            .andExpect(jsonPath("$.totalCents").value(4_450_000))
            .andExpect(jsonPath("$.paymentProvider").value("MERCADOPAGO"));
    }

    @Test
    void givenAnOrderWhoseBuyersEmailBounced_whenTheAdministratorOpensTheDetail_thenAnAlertShowsTheBuyersPhone()
        throws Exception {
        Placed placed = placeOrder();
        pay(placed);
        owner.update("update email_outbox set status = 'SENT', provider_message_id = 're_bounced', sent_at = now() "
            + "where type = 'ORDER_PAID_CUSTOMER'");
        byte[] bounce = """
            {"type":"email.bounced","created_at":"2026-10-14T15:00:00.000Z","data":{"email_id":"re_bounced",
            "to":["ana@example.com"],"bounce":{"type":"Permanent","subType":"General"}}}
            """.getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mvc.perform(post("/api/mail/webhook").contentType(MediaType.APPLICATION_JSON).content(bounce)
                .header("svix-id", "msg_bounce").header("svix-timestamp", timestamp)
                .header("svix-signature", ResendWebhooks.sign("msg_bounce", timestamp, bounce)))
            .andExpect(status().isOk());

        mvc.perform(get(ORDERS + "/{id}", placed.id()).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alerts", contains("BUYER_EMAIL_BOUNCED")))
            .andExpect(jsonPath("$.buyer.phone").value("3425551234"));
    }

    @Test
    void givenAnOrderCancelledBeforeItsPaymentWasApproved_whenOpened_thenItMustBeRefunded() throws Exception {
        Placed placed = placeOrder();
        change(placed, "{\"from\":\"AWAITING_PAYMENT\",\"to\":\"CANCELLED\"}").andExpect(status().isOk());

        pay(placed);

        mvc.perform(get(ORDERS + "/{id}", placed.id()).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.nextStatuses").isEmpty())
            .andExpect(jsonPath("$.alerts", contains("PAID_AFTER_CLOSING")));
    }

    @Test
    void givenNoSuchOrder_whenOpenedOrChanged_thenItIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get(ORDERS + "/{id}", unknown).with(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.problem").value("ORDER_NOT_FOUND"));
        mvc.perform(post(ORDERS + "/{id}/status", unknown).with(ADMIN).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"PAID\",\"to\":\"PREPARING\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void givenRequestsOutOfShape_whenTheyArrive_thenTheyAreBadRequests() throws Exception {
        Placed placed = placeOrder();

        mvc.perform(get(ORDERS).param("status", "LOST").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(ORDERS).param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
        change(placed, "{\"to\":\"CANCELLED\"}").andExpect(status().isBadRequest());
        change(placed, "{\"from\":\"AWAITING_PAYMENT\",\"to\":\"GONE\"}").andExpect(status().isBadRequest());
        mvc.perform(get(ORDERS + "/not-a-uuid").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenManagingOrders_thenAccessIsDenied() throws Exception {
        Placed placed = placeOrder();

        mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS + "/{id}", placed.id()).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(post(ORDERS + "/{id}/status", placed.id()).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"AWAITING_PAYMENT\",\"to\":\"CANCELLED\"}"))
            .andExpect(status().isForbidden());
        assertThat(statusOf(placed)).isEqualTo("AWAITING_PAYMENT");
    }

    private ResultActions change(Placed placed, String body) throws Exception {
        return mvc.perform(post(ORDERS + "/{id}/status", placed.id()).with(ADMIN).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions correct(Placed placed, String number) throws Exception {
        return mvc.perform(put(ORDERS + "/{id}/shipment-tracking-number", placed.id()).with(ADMIN).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("shipmentTrackingNumber", number))));
    }

    private ResultActions track(Placed placed) throws Exception {
        return mvc.perform(get("/api/orders/{token}", placed.trackingToken())).andExpect(status().isOk());
    }

    /** As the webhook does with an approved payment: in its own transaction, as the system. */
    private void pay(Placed placed) {
        CurrentActor.callAsSystem(() -> transactions.execute(status ->
            confirmOrderPayment.execute(new ConfirmOrderPayment.Command(OrderId.of(placed.id())))));
    }

    /** A guest fills a new cart with 2 pijamas and places the order. */
    private Placed placeOrder() throws Exception {
        String cartToken = newToken();
        mvc.perform(post("/api/cart/items").cookie(new Cookie(CART_COOKIE, cartToken)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", pijama, "quantity", 2))))
            .andExpect(status().isOk());
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("name", "Ana Pérez")
            .put("email", "ana@example.com")
            .put("phone", "342 555-1234")
            .put("nationalId", "12.345.678");
        body.putObject("address")
            .put("street", "San Martín")
            .put("number", "1234")
            .putNull("floor")
            .put("city", "Santa Fe")
            .put("province", "SANTA_FE")
            .put("postalCode", "s3000abc");
        body.put("paymentProvider", "MERCADOPAGO");
        body.put("acceptedTerms", true);
        JsonNode placed = json.readTree(mvc.perform(post("/api/checkout").cookie(new Cookie(CART_COOKIE, cartToken))
                .with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
        long number = placed.get("number").asLong();
        UUID id = owner.queryForObject("select id from orders where number = ?", UUID.class, number);
        return new Placed(id, number, placed.get("trackingToken").asText());
    }

    private String statusOf(Placed placed) {
        return owner.queryForObject("select status from orders where id = ?", String.class, placed.id());
    }

    private Integer emails(Placed placed) {
        return owner.queryForObject("select count(*) from email_outbox where reference = ?", Integer.class,
            placed.id());
    }

    private Integer onHand() {
        return owner.queryForObject("select on_hand from stock_level where variant_id = ?", Integer.class, pijama);
    }

    private Integer reserved() {
        return owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class, pijama);
    }

    private UUID sellable() {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama', ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, "pijama-" + product);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 2000000, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8));
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 5, now())", variantId);
        return variantId;
    }

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Placed(UUID id, long number, String trackingToken) {}
}
