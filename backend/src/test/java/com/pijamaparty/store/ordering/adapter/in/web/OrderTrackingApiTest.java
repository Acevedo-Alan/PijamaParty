package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-14 (T-E3-08): the tracking link through the API, with the real security filter chain, Row Level Security and
 * PostgreSQL. A guest places the order through the checkout and comes back later, without its cart cookie, with only
 * the link.
 */
@ApiTest
class OrderTrackingApiTest {

    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MercadoPagoStub mercadoPago;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();
    private UUID pijama;

    OrderTrackingApiTest(MockMvc mvc, ObjectMapper json, MercadoPagoStub mercadoPago, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.mercadoPago = mercadoPago;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** 2 pijamas at ARS 20,000 in the guest's cart; shipping to Santa Fe ARS 4,500. */
    @BeforeEach
    void setUp() throws Exception {
        pijama = sellable(2_000_000, 5);
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values ('SANTA_FE', 450000, "
            + "now())");
        mvc.perform(post("/api/cart/items").cookie(new Cookie(CART_COOKIE, cartToken)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", pijama, "quantity", 2))))
            .andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() {
        mercadoPago.reset();
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, withdrawal_request, order_tracking_token, order_item, orders, "
            + "cart_item, cart, stock_movement, stock_level, product_image, variant, product, size_guide, category, "
            + "shipping_zone_rate");
    }

    @Test
    void givenABuyerBackWithoutPaying_whenOpeningItsTrackingLink_thenTheOrderShowsPendingWithTheOptionToRetry()
        throws Exception {
        JsonNode placed = placeOrder();

        String body = track(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.number").value(placed.get("number").asLong()))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.canRetryPayment").value(true))
            .andExpect(jsonPath("$.buyerFirstName").value("Ana"))
            .andExpect(jsonPath("$.city").value("Santa Fe"))
            .andExpect(jsonPath("$.province").value("SANTA_FE"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].sku").value(startsWith("PP-")))
            .andExpect(jsonPath("$.items[0].productName").value("Pijama"))
            .andExpect(jsonPath("$.items[0].size").value("M"))
            .andExpect(jsonPath("$.items[0].color").value("Rosa"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPriceCents").value(2_000_000))
            .andExpect(jsonPath("$.items[0].lineTotalCents").value(4_000_000))
            .andExpect(jsonPath("$.items[0].image").value(nullValue()))
            .andExpect(jsonPath("$.subtotalCents").value(4_000_000))
            .andExpect(jsonPath("$.discountCents").value(0))
            .andExpect(jsonPath("$.couponCode").value(nullValue()))
            .andExpect(jsonPath("$.shippingCents").value(450_000))
            .andExpect(jsonPath("$.totalCents").value(4_450_000))
            .andExpect(jsonPath("$.shipmentTrackingNumber").value(nullValue()))
            .andReturn().getResponse().getContentAsString();

        // PostgreSQL rounds to microseconds; the checkout answered the clock's own precision.
        assertThat(Instant.parse(json.readTree(body).get("reservationExpiresAt").asText()))
            .isCloseTo(Instant.parse(placed.get("reservationExpiresAt").asText()), within(1, ChronoUnit.MICROS));
    }

    @Test
    void givenAnItemWhoseProductHasAPhoto_whenOpeningItsTrackingLink_thenTheItemHasItsCurrentPhoto() throws Exception {
        JsonNode placed = placeOrder();
        owner.update("""
            insert into product_image (id, product_id, color, storage_key, placeholder, position, upload_key,
                                       content_sha256, created_at)
            select ?, v.product_id, v.color, 'pijama-rosa', 'data:image/webp;base64,UklGRg==', 0, ?, ?, now()
            from variant v
            where v.id = ?
            """, UUID.randomUUID(), UUID.randomUUID(), new byte[32], pijama);

        track(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].image.placeholder").value("data:image/webp;base64,UklGRg=="))
            .andExpect(jsonPath("$.items[0].image.files[0].width").value(400))
            .andExpect(jsonPath("$.items[0].image.files[0].url").value("/uploads/pijama-rosa-400.webp"));
    }

    @Test
    void givenAnOrder_whenOpeningItsTrackingLink_thenNoNationalIdEmailPhoneStreetOrLastNameIsShown() throws Exception {
        JsonNode placed = placeOrder();

        track(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("12345678"))))
            .andExpect(content().string(not(containsString("ana@example.com"))))
            .andExpect(content().string(not(containsString("3425551234"))))
            .andExpect(content().string(not(containsString("San Mart"))))
            .andExpect(content().string(not(containsString("rez"))))
            .andExpect(content().string(not(containsString("S3000ABC"))));
    }

    @Test
    void givenAShippedOrder_whenOpeningItsTrackingLink_thenItShowsTheShipmentTrackingNumberAndNoRetry()
        throws Exception {
        JsonNode placed = placeOrder();
        owner.update("update orders set status = 'SHIPPED', shipment_tracking_number = 'CA123456789AR' "
            + "where number = ?", placed.get("number").asLong());

        track(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SHIPPED"))
            .andExpect(jsonPath("$.shipmentTrackingNumber").value("CA123456789AR"))
            .andExpect(jsonPath("$.canRetryPayment").value(false));
    }

    @Test
    void givenATokenOfNoOrderOrAMalformedOne_whenOpened_thenItIsNotFound() throws Exception {
        placeOrder();

        for (String token : new String[] {newToken(), "short", "a".repeat(42) + "!"}) {
            track(token)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.problem").value("ORDER_NOT_FOUND"));
            retry(token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.problem").value("ORDER_NOT_FOUND"));
        }
    }

    @Test
    void givenAMercadoPagoOrder_whenThePaymentIsRetried_thenTheBuyerPaysOnThePage() throws Exception {
        JsonNode placed = placeOrder();
        assertThat(placed.get("payOnSite").asBoolean()).isTrue();

        retry(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payOnSite").value(true))
            .andExpect(jsonPath("$.paymentUrl").value(nullValue()));

        assertThat(mercadoPago.requests()).isEmpty();
    }

    @Test
    void givenAnApprovedCard_whenItIsSent_thenItIsChargedForTheServersTotalAndTheOrderIsPaid() throws Exception {
        JsonNode placed = placeOrder();

        payWithCard(placed.get("trackingToken").asText(), UUID.randomUUID().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.rejection").value(nullValue()));

        JsonNode charge = mercadoPago.requests().getFirst().json();
        assertThat(charge.get("transaction_amount").decimalValue().movePointRight(2).longValueExact())
            .isEqualTo(placed.get("totalCents").asLong());
        assertThat(charge.get("payer").get("email").asText()).isEqualTo("ana@example.com");
        assertThat(charge.get("external_reference").asText()).isEqualTo(placed.get("number").asText());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(owner.queryForObject(
            "select status from orders where number = ?", String.class, placed.get("number").asLong()))
            .isEqualTo("PAID"));
    }

    @Test
    void givenACardWithoutFunds_whenItIsSent_thenItIsRejectedAndTheOrderStillAwaitsPayment() throws Exception {
        JsonNode placed = placeOrder();
        mercadoPago.cardsAnswer("rejected", "cc_rejected_insufficient_amount");

        payWithCard(placed.get("trackingToken").asText(), UUID.randomUUID().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejection").value("INSUFFICIENT_FUNDS"));

        assertThat(owner.queryForObject("select status from orders where number = ?", String.class,
            placed.get("number").asLong())).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void givenTheSameSubmitTwice_whenItIsSent_thenMercadoPagoGetsTheSameIdempotencyKeyAndChargesOnce()
        throws Exception {
        JsonNode placed = placeOrder();
        // In review, the order still awaits payment: a network retry of the submit reaches MercadoPago again
        mercadoPago.cardsAnswer("in_process", "pending_contingency");
        String key = UUID.randomUUID().toString();

        payWithCard(placed.get("trackingToken").asText(), key).andExpect(status().isOk());
        payWithCard(placed.get("trackingToken").asText(), key).andExpect(status().isOk());

        var charges = mercadoPago.requests().stream().filter(request -> request.method().equals("POST")).toList();
        assertThat(charges).hasSize(2);
        assertThat(charges.get(1).idempotencyKey()).isEqualTo(charges.get(0).idempotencyKey());
        assertThat(owner.queryForObject("select count(*) from payment_event", Integer.class)).isOne();
    }

    @Test
    void givenACardInReview_whenAnotherCardIsSentWithANewKey_thenItIs409ChargeInProgressAndNothingIsCharged()
        throws Exception {
        JsonNode placed = placeOrder();
        mercadoPago.cardsAnswer("in_process", "pending_contingency");
        String token = placed.get("trackingToken").asText();
        payWithCard(token, UUID.randomUUID().toString()).andExpect(status().isOk());
        int charges = mercadoPago.requests().stream().filter(request -> request.method().equals("POST")).toList()
            .size();

        payWithCard(token, UUID.randomUUID().toString())
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("CHARGE_IN_PROGRESS"));

        assertThat(mercadoPago.requests().stream().filter(request -> request.method().equals("POST")).count())
            .isEqualTo(charges);
    }

    @Test
    void givenMercadoPagoDown_whenACardIsSent_thenItAnswers503WithRetryAfterAndTheOrderStillAwaitsPayment()
        throws Exception {
        JsonNode placed = placeOrder();
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");

        payWithCard(placed.get("trackingToken").asText(), UUID.randomUUID().toString())
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        assertThat(owner.queryForObject("select status from orders where number = ?", String.class,
            placed.get("number").asLong())).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void givenACardWithoutOrWithAnInvalidIdempotencyKey_whenItIsSent_thenItIsABadRequestAndNothingIsCharged()
        throws Exception {
        JsonNode placed = placeOrder();
        String token = placed.get("trackingToken").asText();

        mvc.perform(post("/api/orders/{token}/card-payment", token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CARD))
            .andExpect(status().isBadRequest());
        payWithCard(token, "not-a-uuid").andExpect(status().isBadRequest());

        assertThat(mercadoPago.requests()).isEmpty();
    }

    @Test
    void givenAnExpiredOrder_whenACardIsSent_thenItIsNotPayableAndNothingIsCharged() throws Exception {
        JsonNode placed = placeOrder();
        owner.update("update orders set status = 'EXPIRED' where number = ?", placed.get("number").asLong());

        payWithCard(placed.get("trackingToken").asText(), UUID.randomUUID().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("NOT_PAYABLE"));

        assertThat(mercadoPago.requests()).isEmpty();
    }

    @Test
    void givenAnExpiredOrder_whenThePaymentIsRetried_thenItIsNotPayableAndTheLinkShowsNoRetry() throws Exception {
        JsonNode placed = placeOrder();
        owner.update("update orders set status = 'EXPIRED' where number = ?", placed.get("number").asLong());
        int calls = mercadoPago.requests().size();

        retry(placed.get("trackingToken").asText())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("NOT_PAYABLE"));
        track(placed.get("trackingToken").asText())
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.canRetryPayment").value(false));

        assertThat(mercadoPago.requests()).hasSize(calls);
    }

    @Test
    void givenARetryWithoutTheCsrfToken_whenItArrives_thenItIsForbidden() throws Exception {
        JsonNode placed = placeOrder();

        mvc.perform(post("/api/orders/{token}/retry-payment", placed.get("trackingToken").asText()))
            .andExpect(status().isForbidden());
    }

    private JsonNode placeOrder() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("firstName", "Ana")
            .put("lastName", "Pérez")
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
        String placed = mvc.perform(post("/api/checkout").cookie(new Cookie(CART_COOKIE, cartToken)).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(placed);
    }

    /** Without the cart cookie: the link alone opens the order. */
    private ResultActions track(String token) throws Exception {
        return mvc.perform(get("/api/orders/{token}", token));
    }

    /** What MercadoPago's card form answers for a card: a token, the brand, installments and the holder's ID. */
    private static final String CARD = """
        {"cardToken": "card-token-1", "paymentMethodId": "visa", "issuerId": "310", "installments": 3,
         "identificationType": "DNI", "identificationNumber": "12345678"}
        """;

    private ResultActions payWithCard(String token, String idempotencyKey) throws Exception {
        return mvc.perform(post("/api/orders/{token}/card-payment", token).with(csrf())
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON).content(CARD));
    }

    private ResultActions retry(String token) throws Exception {
        return mvc.perform(post("/api/orders/{token}/retry-payment", token).with(csrf()));
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

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
