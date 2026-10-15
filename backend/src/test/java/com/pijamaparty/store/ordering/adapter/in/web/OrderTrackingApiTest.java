package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
        UUID pijama = sellable(2_000_000, 5);
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
        owner.execute("truncate idempotency_key, payment_event, payment, withdrawal_request, order_tracking_token, order_item, orders, "
            + "cart_item, cart, stock_movement, stock_level, product_image, variant, product, category, "
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
    void givenAnOrderWithItsCheckoutOpen_whenThePaymentIsRetried_thenTheSameCheckoutIsAnswered() throws Exception {
        JsonNode placed = placeOrder();

        retry(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentUrl").value(placed.get("paymentUrl").asText()));

        assertThat(mercadoPago.requests()).as("only the checkout created the preference").hasSize(1);
    }

    @Test
    void givenMercadoPagoDownAtTheCheckout_whenThePaymentIsRetriedOnceItIsBack_thenTheBuyerIsSentToANewCheckout()
        throws Exception {
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");
        JsonNode placed = placeOrder();
        assertThat(placed.get("paymentUrl").isNull()).isTrue();
        mercadoPago.reset();

        String body = retry(placed.get("trackingToken").asText())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentUrl").value(startsWith(MercadoPagoStub.INIT_POINT)))
            .andReturn().getResponse().getContentAsString();

        assertThat(owner.queryForObject("select p.checkout_url from payment p join orders o on o.id = p.order_id "
            + "where o.number = ?", String.class, placed.get("number").asLong()))
            .isEqualTo(json.readTree(body).get("paymentUrl").asText());
        assertThat(mercadoPago.lastRequest().json().get("external_reference").asText())
            .isEqualTo(placed.get("number").asText());
    }

    @Test
    void givenMercadoPagoDown_whenThePaymentIsRetried_thenItAnswers503WithRetryAfterAndTheOrderStillAwaitsPayment()
        throws Exception {
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");
        JsonNode placed = placeOrder();

        retry(placed.get("trackingToken").asText())
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        assertThat(owner.queryForObject("select status from orders where number = ?", String.class,
            placed.get("number").asLong())).isEqualTo("AWAITING_PAYMENT");
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
