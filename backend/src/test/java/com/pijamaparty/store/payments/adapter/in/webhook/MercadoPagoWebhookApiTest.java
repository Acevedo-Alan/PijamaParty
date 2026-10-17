package com.pijamaparty.store.payments.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.ExpireOverdueOrder;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStubConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-15, UC-16: MercadoPago's notifications through the API, with the real security filter chain, Row Level Security
 * and PostgreSQL, against the MercadoPago stub. The order is placed through the cart and checkout API.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoWebhookApiTest {

    private static final String WEBHOOK = "/api/payments/mercadopago/webhook";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PAYMENT_ID = "9876543210";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MercadoPagoStub mercadoPago;
    private final ExpireOverdueOrder expireOverdueOrder;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();
    private UUID pijama;

    MercadoPagoWebhookApiTest(MockMvc mvc, ObjectMapper json, MercadoPagoStub mercadoPago,
                              ExpireOverdueOrder expireOverdueOrder, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.mercadoPago = mercadoPago;
        this.expireOverdueOrder = expireOverdueOrder;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** 2 pijamas at ARS 20,000 in the guest's cart, 5 in stock; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void setUp() throws Exception {
        pijama = sellable(2_000_000, 5);
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values ('SANTA_FE', 450000, "
            + "now())");
        mvc.perform(guest(post("/api/cart/items")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", pijama, "quantity", 2))))
            .andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() {
        mercadoPago.reset();
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, email_outbox, withdrawal_request, "
            + "order_tracking_token, "
            + "order_item, orders, cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, "
            + "stock_level, product_image, variant, product, category, shipping_zone_rate");
    }

    @Test
    void givenAnApprovedPayment_whenNotified_thenTheOrderIsPaidItsStockDeductedAndItsCouponUsed(CapturedOutput logs)
        throws Exception {
        UUID coupon = applyCoupon();
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(stock()).containsEntry("on_hand", 3).containsEntry("reserved", 0);
        assertThat(payment(placed)).containsEntry("status", "APPROVED")
            .containsEntry("external_payment_id", PAYMENT_ID);
        assertThat(owner.queryForObject("select count(*) from coupon_redemption where coupon_id = ?", Long.class,
            coupon)).isOne();
        assertThat(mercadoPago.lastRequest().path()).isEqualTo("/v1/payments/" + PAYMENT_ID);
        assertThat(logLines(logs)).doesNotContain("ana@example.com", "12345678", "Ana P");
        // UC-17: the buyer's and the administrator's emails, enqueued with the payment (RNF-23)
        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
    }

    @Test
    void givenTheSameApprovalNotifiedTwice_whenTheSecondIsProcessed_thenNothingIsDeductedOrUsedAgain()
        throws Exception {
        UUID coupon = applyCoupon();
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());
        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(stock()).containsEntry("on_hand", 3).containsEntry("reserved", 0);
        assertThat(owner.queryForObject("select count(*) from stock_movement where type = 'DEDUCTION'", Long.class))
            .isOne();
        assertThat(owner.queryForObject("select count(*) from coupon_redemption where coupon_id = ?", Long.class,
            coupon)).isOne();
    }

    @Test
    void givenAnOrderWhoseConfirmationIsProcessedTwice_whenItFinishes_thenEachEmailIsEnqueuedOnce() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());
        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
    }

    @Test
    void givenAnOrderThatExpiresUnpaid_whenItFinishes_thenNoEmailIsEnqueued() throws Exception {
        Placed placed = placeOrder();
        owner.update("update orders set created_at = now() - interval '31 minutes', "
            + "reservation_expires_at = now() - interval '1 minute' where number = ?",
            placed.number());

        CurrentActor.callAsSystem(expireOverdueOrder::execute);

        assertThat(orderStatus(placed)).isEqualTo("EXPIRED");
        assertThat(emails(placed)).isEmpty();
    }

    @Test
    void givenTwoIdenticalNotificationsAtOnce_whenProcessed_thenOnlyOneHasEffects() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = List.of(
                executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return notifyPayment(PAYMENT_ID).andReturn().getResponse().getStatus();
                }),
                executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return notifyPayment(PAYMENT_ID).andReturn().getResponse().getStatus();
                }));
            start.countDown();

            for (Future<Integer> answer : answers) {
                assertThat(answer.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(stock()).containsEntry("on_hand", 3).containsEntry("reserved", 0);
        assertThat(owner.queryForObject("select count(*) from stock_movement where type = 'DEDUCTION'", Long.class))
            .isOne();
        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
        // RNF-20.3: the second insert waited for the first transaction and recorded nothing
        assertThat(paymentEvents()).containsExactly(Map.of("provider", "MERCADOPAGO", "external_payment_id",
            PAYMENT_ID, "status", "APPROVED", "order_number", placed.number()));
    }

    @Test
    void givenAPendingPaymentNotified_whenItIsApprovedAndNotifiedAgain_thenTheOrderIsPaidAndBothAreRecorded()
        throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "in_process", placed.pesos());
        notifyPayment(PAYMENT_ID).andExpect(status().isOk());
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(paymentEvents()).extracting(event -> event.get("status"))
            .containsExactlyInAnyOrder("PENDING", "APPROVED");
    }

    @Test
    void givenARejectedPayment_whenNotified_thenTheOrderKeepsWaitingWithItsStockReserved() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "rejected", placed.pesos());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(stock()).containsEntry("on_hand", 5).containsEntry("reserved", 2);
        assertThat(payment(placed)).containsEntry("status", "REJECTED")
            .containsEntry("external_payment_id", PAYMENT_ID);
        assertThat(emails(placed)).isEmpty();
    }

    @Test
    void givenARejectedAttempt_whenTheBuyerPaysWithAnotherCard_thenTheOrderIsPaid() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment("111", placed.number(), "rejected", placed.pesos());
        mercadoPago.holdsPayment("222", placed.number(), "approved", placed.pesos());

        notifyPayment("111").andExpect(status().isOk());
        notifyPayment("222").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(payment(placed)).containsEntry("status", "APPROVED").containsEntry("external_payment_id", "222");
    }

    @Test
    void givenAPaymentApprovedAfterTheOrderExpired_whenNotified_thenTheOrderStaysExpiredAndItsStockAvailable(
        CapturedOutput logs) throws Exception {
        Placed placed = placeOrder();
        owner.update("update orders set created_at = now() - interval '31 minutes', "
            + "reservation_expires_at = now() - interval '1 minute' where number = ?",
            placed.number());
        CurrentActor.callAsSystem(expireOverdueOrder::execute);
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("EXPIRED");
        assertThat(stock()).containsEntry("on_hand", 5).containsEntry("reserved", 0);
        assertThat(payment(placed)).containsEntry("status", "APPROVED");
        assertThat(logLines(logs)).contains("Order " + placed.number() + " was paid").contains("refund");
        // UC-17: a payment approved too late sends no email either
        assertThat(emails(placed)).isEmpty();
    }

    @Test
    void givenAPaymentForAnotherAmount_whenNotified_thenTheOrderIsNotPaid() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", new BigDecimal("1.00"));

        notifyPayment(PAYMENT_ID).andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(payment(placed)).containsEntry("status", "PENDING");
    }

    @Test
    void givenAPaymentThatDoesNotExist_whenNotified_thenItIsIgnoredWithAWarningWithoutPersonalData(
        CapturedOutput logs) throws Exception {
        Placed placed = placeOrder();
        mercadoPago.holdsPayment("555", 999_999, "approved", placed.pesos());

        notifyPayment("404404").andExpect(status().isOk());
        notifyPayment("555").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(payment(placed)).containsEntry("status", "PENDING");
        assertThat(logLines(logs)).contains("MercadoPago does not know payment 404404")
            .contains("MercadoPago payment 555 is not for an order of this store")
            .doesNotContain("ana@example.com", "12345678");
    }

    @Test
    void givenAnInvalidOrMissingSignature_whenNotified_thenItIsRefusedWithoutAskingMercadoPago(CapturedOutput logs)
        throws Exception {
        Placed placed = placeOrder();
        mercadoPago.reset();
        mercadoPago.holdsPayment(PAYMENT_ID, placed.number(), "approved", placed.pesos());
        String ts = Long.toString(Instant.now().toEpochMilli());
        String requestId = UUID.randomUUID().toString();

        mvc.perform(post(WEBHOOK).param("type", "payment").param("data.id", PAYMENT_ID)
                .header("x-request-id", requestId)
                .header("x-signature", MercadoPagoSignatureTest.header(ts,
                    MercadoPagoSignatureTest.sign("not-the-secret", PAYMENT_ID, requestId, ts))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(WEBHOOK).param("type", "payment").param("data.id", PAYMENT_ID))
            .andExpect(status().isUnauthorized());

        assertThat(mercadoPago.requests()).isEmpty();
        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(logLines(logs)).contains("MercadoPago notification refused").doesNotContain(requestId);
    }

    @Test
    void givenMercadoPagoDown_whenNotified_thenItAsksForTheNotificationAgain() throws Exception {
        Placed placed = placeOrder();
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");

        notifyPayment(PAYMENT_ID)
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().exists("Retry-After"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void givenANotificationOfAnotherKind_whenNotified_thenItIsAcknowledgedWithoutAskingMercadoPago()
        throws Exception {
        placeOrder();
        mercadoPago.reset();

        notify("merchant_order", PAYMENT_ID).andExpect(status().isOk());
        notify("payment", "not-a-number").andExpect(status().isOk());

        assertThat(mercadoPago.requests()).isEmpty();
    }
    private record Placed(long number, long totalCents) {

        BigDecimal pesos() {
            return BigDecimal.valueOf(totalCents, 2);
        }
    }

    private Placed placeOrder() throws Exception {
        String body = mvc.perform(guest(post("/api/checkout")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode placed = json.readTree(body);
        return new Placed(placed.get("number").asLong(), placed.get("totalCents").asLong());
    }

    /** A 10% coupon applied to the guest's cart; answers its id. */
    private UUID applyCoupon() throws Exception {
        UUID coupon = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, active, created_at, updated_at)
            values (?, 'VERANO10', 'PERCENTAGE', 10, true, now(), now())
            """, coupon);
        mvc.perform(guest(post("/api/cart/coupon")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", "VERANO10")))).andExpect(status().isOk());
        return coupon;
    }

    /** As MercadoPago sends it: signed, without a session or a CSRF token. */
    private ResultActions notifyPayment(String paymentId) throws Exception {
        return notify("payment", paymentId);
    }

    private ResultActions notify(String type, String dataId) throws Exception {
        String ts = Long.toString(Instant.now().toEpochMilli());
        String requestId = UUID.randomUUID().toString();
        return mvc.perform(post(WEBHOOK).param("data.id", dataId).param("type", type)
            .header("x-request-id", requestId)
            .header("x-signature", MercadoPagoSignatureTest.header(ts, MercadoPagoSignatureTest.sign(
                MercadoPagoStubConfiguration.WEBHOOK_SECRET, dataId, requestId, ts)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"payment.updated\",\"type\":\"" + type + "\",\"data\":{\"id\":\"" + dataId
                + "\"}}"));
    }

    /** What the application logged, without MockMvc's dumps of the requests (which carry the buyer's data). */
    private static String logLines(CapturedOutput output) {
        return output.getAll().lines()
            .filter(line -> line.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*"))
            .collect(Collectors.joining("\n"));
    }

    private String orderStatus(Placed placed) {
        return owner.queryForObject("select status from orders where number = ?", String.class, placed.number());
    }

    /** UC-17: the types of the emails enqueued for the order. */
    private List<String> emails(Placed placed) {
        return owner.queryForList("""
            select e.type from email_outbox e join orders o on o.id = e.reference where o.number = ?
            """, String.class, placed.number());
    }

    private Map<String, Object> payment(Placed placed) {
        return owner.queryForMap("select * from payment where order_number = ?", placed.number());
    }

    private List<Map<String, Object>> paymentEvents() {
        return owner.queryForList("select provider, external_payment_id, status, order_number from payment_event");
    }

    private Map<String, Object> stock() {
        return owner.queryForMap("select on_hand, reserved from stock_level where variant_id = ?", pijama);
    }

    private ObjectNode order() {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("name", "Ana Pérez")
            .put("email", "ana@example.com")
            .put("phone", "342 555-1234")
            .put("nationalId", "12.345.678");
        body.putObject("address")
            .put("street", "San Martín")
            .put("number", "1234")
            .put("city", "Santa Fe")
            .put("province", "SANTA_FE")
            .put("postalCode", "S3000ABC");
        body.put("paymentProvider", "MERCADOPAGO");
        body.put("acceptedTerms", true);
        return body;
    }

    private UUID sellable(long priceCents, int available) {
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
            values (?, ?, ?, 'M', 0, 'Rosa', 0, ?, true, now(), now())
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8), priceCents);
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            available);
        return variantId;
    }

    private MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(CART_COOKIE, cartToken));
    }

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
