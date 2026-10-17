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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStub;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStubConfiguration;
import com.pijamaparty.store.payments.application.ConfirmPayment;
import com.pijamaparty.store.payments.application.ReconcileCheckouts;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-15 (T-E3b-03): MODO's notifications and the reconciliation, through the API with the real security filter
 * chain, Row Level Security and PostgreSQL, against the MODO stub. The order is placed with MODO through the cart and
 * checkout API, so its payment request is a real attempt of the store.
 */
@ApiTest
@Import(ModoStubConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ModoWebhookApiTest {

    private static final String WEBHOOK = "/api/payments/modo/webhook";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final ModoStub modo;
    private final ReconcileCheckouts reconcileCheckouts;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();
    private UUID pijama;

    ModoWebhookApiTest(MockMvc mvc, ObjectMapper json, ModoStub modo, ReconcileCheckouts reconcileCheckouts,
                       PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.modo = modo;
        this.reconcileCheckouts = reconcileCheckouts;
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
        modo.reset();
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, email_outbox, "
            + "withdrawal_request, order_tracking_token, order_item, orders, cart_item, cart, coupon_scope, "
            + "coupon_redemption, coupon, stock_movement, stock_level, product_image, variant, product, category, "
            + "shipping_zone_rate");
    }

    @Test
    void givenAnApprovedPayment_whenMODONotifiesIt_thenTheOrderIsPaidAndThePayerIsNotLogged(CapturedOutput logs)
        throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "ACCEPTED", placed.pesos());

        notify(placed.request(), "ACCEPTED").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(stock()).containsEntry("on_hand", 3).containsEntry("reserved", 0);
        assertThat(payment(placed)).containsEntry("status", "APPROVED")
            .containsEntry("external_payment_id", placed.request());
        assertThat(paymentEvents()).containsExactly(Map.of("provider", "MODO", "external_payment_id",
            placed.request(), "status", "APPROVED", "order_number", placed.number()));
        assertThat(modo.requestsTo("/v2/payment-requests/" + placed.request() + "/data")).hasSize(1);
        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
        assertThat(logLines(logs)).contains("MODO payment request " + placed.request() + " approved order")
            .doesNotContain("12345678", "Perez", "490698", "ana@example.com");
    }

    @Test
    void givenTheSameApprovalNotifiedTwice_whenTheSecondIsProcessed_thenNothingIsDeductedAgain() throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "ACCEPTED", placed.pesos());
        notify(placed.request(), "ACCEPTED").andExpect(status().isOk());

        notify(placed.request(), "ACCEPTED").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(deductions()).isOne();
        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
    }

    @Test
    void givenARejectedPayment_whenMODONotifiesIt_thenTheOrderKeepsWaitingWithItsStockReserved() throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "REJECTED", placed.pesos());

        notify(placed.request(), "REJECTED").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(stock()).containsEntry("on_hand", 5).containsEntry("reserved", 2);
        assertThat(payment(placed)).containsEntry("status", "REJECTED");
    }

    @Test
    void givenANotificationWhoseSignatureDoesNotVerify_whenItArrives_thenItIsRefusedWith401AndNothingIsProcessed(
        CapturedOutput logs) throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "ACCEPTED", placed.pesos());
        ObjectNode forged = (ObjectNode) json.readTree(modo.notification(placed.request(), "ACCEPTED"));
        forged.set("signature", ModoStub.signature(payloadOf(forged),
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("modo-key-0").build(),
            ModoStub.signer(ModoStub.newKey("modo-key-0"))));
        ObjectNode unsigned = forged.deepCopy();
        unsigned.remove("signature");

        send(forged.toString()).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        send(unsigned.toString()).andExpect(status().isUnauthorized());
        send("not json").andExpect(status().isUnauthorized());

        assertThat(modo.requestsTo("/v2/payment-requests/" + placed.request() + "/data")).isEmpty();
        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentEvents()).isEmpty();
        assertThat(logLines(logs)).contains("MODO notification refused").doesNotContain("12345678", "Perez");
    }

    @Test
    void givenABodyLongerThan64KB_whenItArrives_thenItIsRefusedWith401WithoutBeingRead() throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "ACCEPTED", placed.pesos());
        ObjectNode padded = (ObjectNode) json.readTree(modo.notification(placed.request(), "ACCEPTED"));
        padded.put("padding", "x".repeat(64 * 1024));

        send(padded.toString()).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(modo.requestsTo("/v2/payment-requests/" + placed.request() + "/data")).isEmpty();
        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentEvents()).isEmpty();
    }

    @Test
    void givenAPaymentRequestIdThatIsNotMODOs_whenNotified_thenItIsAcknowledgedWithoutAskingMODO() throws Exception {
        Placed placed = placeOrder();

        for (String id : List.of("", "../../stores/companies/token", "pr 1", "a".repeat(101))) {
            notify(id, "ACCEPTED").andExpect(status().isOk());
        }

        assertThat(modo.requests()).noneMatch(request -> request.path().endsWith("/data"));
        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentEvents()).isEmpty();
    }

    @Test
    void givenAnUnsignedFieldThatDiffersFromTheSignedOne_whenItArrives_thenOnlyTheSignedOneCounts() throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "REJECTED", placed.pesos());
        ObjectNode notification = (ObjectNode) json.readTree(modo.notification(placed.request(), "SCANNED"));
        notification.put("status", "ACCEPTED");

        send(notification.toString()).andExpect(status().isOk());

        assertThat(modo.requestsTo("/v2/payment-requests/" + placed.request() + "/data")).isEmpty();
        assertThat(payment(placed)).containsEntry("status", "PENDING");
    }

    @Test
    void givenAStatusThatEndsNothing_whenMODONotifiesIt_thenItIsAcknowledgedWithoutAskingMODO() throws Exception {
        Placed placed = placeOrder();

        for (String status : List.of("CREATED", "SCANNED", "PROCESSING")) {
            notify(placed.request(), status).andExpect(status().isOk());
        }

        assertThat(modo.requestsTo("/v2/payment-requests/" + placed.request() + "/data")).isEmpty();
        assertThat(paymentEvents()).isEmpty();
    }

    @Test
    void givenAPaymentRequestThisStoreDidNotCreate_whenNotified_thenItIsIgnoredAndRecordedNowhere(CapturedOutput logs)
        throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment("f15c5daf-a91e-4ec2-a2fd-86d656c82f4c", "ACCEPTED", placed.pesos());

        notify("f15c5daf-a91e-4ec2-a2fd-86d656c82f4c", "ACCEPTED").andExpect(status().isOk());

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
        assertThat(paymentEvents()).isEmpty();
        assertThat(logLines(logs))
            .contains("MODO payment request f15c5daf-a91e-4ec2-a2fd-86d656c82f4c is not a checkout of this store");
    }

    @Test
    void givenMODODown_whenItNotifies_thenItIsAskedToDeliverAgain() throws Exception {
        Placed placed = placeOrder();
        modo.answerData(500, "{\"message\":\"internal_error\"}");

        notify(placed.request(), "ACCEPTED")
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().exists("Retry-After"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void givenAnApprovedPaymentWhoseNotificationNeverArrived_whenTheReconciliationRuns_thenTheOrderIsPaidExactlyOnce()
        throws Exception {
        Placed placed = placeOrder();
        modo.holdsPayment(placed.request(), "ACCEPTED", placed.pesos());
        createdTwoMinutesAgo(placed);

        ReconcileCheckouts.Result first = CurrentActor.callAsSystem(reconcileCheckouts::execute);
        ReconcileCheckouts.Result second = CurrentActor.callAsSystem(reconcileCheckouts::execute);
        notify(placed.request(), "ACCEPTED").andExpect(status().isOk());

        assertThat(first.reconciled()).extracting(ReconcileCheckouts.Reconciled::result)
            .containsExactly(new ConfirmPayment.Result.Approved(placed.number()));
        assertThat(second.reconciled()).isEmpty();
        assertThat(orderStatus(placed)).isEqualTo("PAID");
        assertThat(deductions()).isOne();
        assertThat(emails(placed)).containsExactlyInAnyOrder("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN");
        assertThat(paymentEvents()).hasSize(1);
    }

    @Test
    void givenAPaymentRequestNobodyPaid_whenTheReconciliationRuns_thenTheOrderKeepsWaiting() throws Exception {
        Placed placed = placeOrder();
        createdTwoMinutesAgo(placed);

        ReconcileCheckouts.Result result = CurrentActor.callAsSystem(reconcileCheckouts::execute);

        assertThat(result.reconciled()).extracting(ReconcileCheckouts.Reconciled::result)
            .containsExactly(new ConfirmPayment.Result.NotFoundAtProvider());
        assertThat(orderStatus(placed)).isEqualTo("AWAITING_PAYMENT");
    }

    private record Placed(long number, long totalCents, String request) {

        /** As MODO writes amounts: pesos with three decimals. */
        String pesos() {
            return BigDecimal.valueOf(totalCents, 2).setScale(3).toPlainString();
        }
    }

    /** Placed with MODO through the API; {@code request} is the payment request MODO created for it. */
    private Placed placeOrder() throws Exception {
        String body = mvc.perform(guest(post("/api/checkout")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode placed = json.readTree(body);
        long number = placed.get("number").asLong();
        String request = owner.queryForObject("""
            select c.checkout_id from payment_checkout c join payment p on p.id = c.payment_id
            where p.order_number = ? and p.provider = 'MODO'
            """, String.class, number);
        return new Placed(number, placed.get("totalCents").asLong(), request);
    }

    /** The reconciliation leaves alone a payment request younger than a minute. */
    private void createdTwoMinutesAgo(Placed placed) {
        owner.update("update payment_checkout set created_at = now() - interval '2 minutes' where checkout_id = ?",
            placed.request());
    }

    /** As MODO sends it: signed, without a session or a CSRF token. */
    private ResultActions notify(String request, String status) throws Exception {
        return send(modo.notification(request, status));
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String payloadOf(ObjectNode notification) {
        ObjectNode payload = notification.deepCopy();
        payload.remove("signature");
        return payload.toString();
    }

    /** What the application logged, without MockMvc's dumps of the requests (which carry the payer's data). */
    private static String logLines(CapturedOutput output) {
        return output.getAll().lines()
            .filter(line -> line.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*"))
            .collect(Collectors.joining("\n"));
    }

    private String orderStatus(Placed placed) {
        return owner.queryForObject("select status from orders where number = ?", String.class, placed.number());
    }

    private long deductions() {
        return owner.queryForObject("select count(*) from stock_movement where type = 'DEDUCTION'", Long.class);
    }

    /** UC-17: the types of the emails enqueued for the order. */
    private List<String> emails(Placed placed) {
        return owner.queryForList("""
            select e.type from email_outbox e join orders o on o.id = e.reference where o.number = ?
            """, String.class, placed.number());
    }

    private Map<String, Object> payment(Placed placed) {
        return owner.queryForMap("select * from payment where order_number = ? and provider = 'MODO'",
            placed.number());
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
        body.put("paymentProvider", "MODO");
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
