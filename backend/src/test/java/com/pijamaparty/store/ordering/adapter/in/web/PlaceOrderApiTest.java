package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-13, UC-14, UC-16: confirming the checkout through the API, with the real security filter chain, Row Level
 * Security and PostgreSQL. The guest fills its cart through the cart API.
 */
@ApiTest
class PlaceOrderApiTest {

    private static final String CHECKOUT = "/api/checkout";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();
    private UUID pijama;

    PlaceOrderApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
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
        owner.execute("truncate order_item, orders, cart_item, cart, coupon_redemption, coupon, stock_movement, "
            + "stock_level, product_image, variant, product, category, shipping_zone_rate");
    }

    @Test
    void givenAReadyCart_whenTheBuyerConfirms_thenTheOrderAwaitsPaymentWithItsStockReservedAndTheCartEmpty()
        throws Exception {
        Instant before = Instant.now();

        String body = checkout(order()).andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.totalCents").value(4_450_000))
            .andExpect(content().string(not(containsString("12345678"))))
            .andReturn().getResponse().getContentAsString();

        JsonNode placed = json.readTree(body);
        long number = placed.get("number").asLong();
        String trackingToken = placed.get("trackingToken").asText();
        Instant expiresAt = Instant.parse(placed.get("reservationExpiresAt").asText());
        assertThat(number).isGreaterThanOrEqualTo(1001);
        assertThat(trackingToken).hasSize(43);
        assertThat(expiresAt).isBetween(before.plus(30, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES));
        var order = owner.queryForMap("select * from orders where number = ?", number);
        assertThat(order.get("tracking_token_hash")).isEqualTo(new TrackingToken(trackingToken).hash());
        assertThat(order.get("status")).isEqualTo("AWAITING_PAYMENT");
        assertThat(order.get("user_id")).isNull();
        assertThat(order.get("customer_name")).isEqualTo("Ana Pérez");
        assertThat(order.get("customer_email")).isEqualTo("ana@example.com");
        assertThat(order.get("customer_phone")).isEqualTo("3425551234");
        assertThat(order.get("national_id")).isEqualTo("12345678");
        assertThat(order.get("postal_code")).isEqualTo("S3000ABC");
        assertThat(order.get("subtotal_cents")).isEqualTo(4_000_000L);
        assertThat(order.get("shipping_cents")).isEqualTo(450_000L);
        assertThat(order.get("total_cents")).isEqualTo(4_450_000L);
        assertThat(order.get("terms_version")).isEqualTo("2026-10");
        assertThat(order.get("payment_provider")).isEqualTo("MERCADOPAGO");
        var item = owner.queryForMap("select * from order_item where order_id = ?", order.get("id"));
        assertThat(item.get("variant_id")).isEqualTo(pijama);
        assertThat(item.get("sku")).isEqualTo("PP-" + pijama.toString().substring(0, 8));
        assertThat(item.get("product_name")).isEqualTo("Pijama");
        assertThat(item.get("quantity")).isEqualTo(2);
        assertThat(item.get("unit_price_cents")).isEqualTo(2_000_000L);
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isEqualTo(2);
        assertThat(owner.queryForObject("select count(*) from cart_item", Integer.class)).isZero();
    }

    @Test
    void givenAPlacedOrder_whenTheSameCheckoutArrivesAgain_thenItFindsTheCartEmptyAndNoOtherOrderIsCreated()
        throws Exception {
        String key = UUID.randomUUID().toString();
        checkout(order(), key).andExpect(status().isCreated());

        checkout(order(), key)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));

        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isEqualTo(2);
    }

    @Test
    void givenACheckoutWithoutIdempotencyKeyOrWithOneThatIsNotAUuid_whenItArrives_thenItIsRejectedWith400()
        throws Exception {
        mvc.perform(guest(post(CHECKOUT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(order())))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        checkout(order(), "not-a-uuid")
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertNoOrder();
    }

    @Test
    void givenABodyThatIsNotOfTheShape_whenItArrives_thenItIsRejectedWith400() throws Exception {
        ObjectNode withoutName = order();
        ((ObjectNode) withoutName.get("buyer")).remove("name");
        ObjectNode unknownProvince = order();
        ((ObjectNode) unknownProvince.get("address")).put("province", "ATLANTIDA");
        ObjectNode unknownProvider = order();
        unknownProvider.put("paymentProvider", "MODO");
        ObjectNode tooLong = order();
        ((ObjectNode) tooLong.get("address")).put("street", "s".repeat(301));

        for (ObjectNode body : new ObjectNode[] {withoutName, unknownProvince, unknownProvider, tooLong}) {
            checkout(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertNoOrder();
    }

    @Test
    void givenAnEmptyRequiredFieldAndANationalIdWithAnInvalidFormat_whenConfirming_thenEachIsNamedWith422()
        throws Exception {
        ObjectNode body = order();
        ((ObjectNode) body.get("buyer")).put("nationalId", "12.345");
        ((ObjectNode) body.get("address")).put("city", " ");

        checkout(body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_BUYER_DATA"))
            .andExpect(jsonPath("$.fields.length()").value(2))
            .andExpect(jsonPath("$.fields[0]").value("nationalId"))
            .andExpect(jsonPath("$.fields[1]").value("city"))
            .andExpect(content().string(not(containsString("12.345"))));
        assertNoOrder();
    }

    @Test
    void givenTheTermsNotAccepted_whenConfirming_thenItIsRejectedWith422() throws Exception {
        checkout(order().put("acceptedTerms", false))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("TERMS_NOT_ACCEPTED"));
        assertNoOrder();
    }

    @Test
    void givenACartWithAVariantOutOfStockOrANewPrice_whenConfirming_thenTheCartIsNotReady() throws Exception {
        owner.update("update variant set price_cents = 2100000 where id = ?", pijama);

        checkout(order())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("CART_NOT_READY"));

        mvc.perform(guest(post("/api/cart/acknowledge-prices")).with(csrf())).andExpect(status().isOk());
        owner.update("update stock_level set on_hand = 1 where variant_id = ?", pijama);

        checkout(order())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("CART_NOT_READY"));
        assertNoOrder();
    }

    @Test
    void givenACouponThatNoLongerApplies_whenConfirming_thenItIsRefusedWithItsStatus() throws Exception {
        owner.update("""
            insert into coupon (id, code, type, discount_value, active, created_at, updated_at)
            values (?, 'VERANO10', 'PERCENTAGE', 10, true, now(), now())
            """, UUID.randomUUID());
        mvc.perform(guest(post("/api/cart/coupon")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", "VERANO10")))).andExpect(status().isOk());
        owner.update("update coupon set active = false");

        checkout(order())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("COUPON_NOT_APPLICABLE"))
            .andExpect(jsonPath("$.couponStatus").value("INACTIVE"));
        assertNoOrder();
    }

    @Test
    void givenAProvinceWithoutShipping_whenConfirming_thenItIsRejectedWith422() throws Exception {
        ObjectNode body = order();
        ((ObjectNode) body.get("address")).put("province", "JUJUY");

        checkout(body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("NO_SHIPPING"))
            .andExpect(jsonPath("$.province").value("JUJUY"));
        assertNoOrder();
    }

    @Test
    void givenNoCartOrTheAdministrator_whenConfirming_thenThereIsNoCartToBuy() throws Exception {
        mvc.perform(post(CHECKOUT).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));
        checkoutRequest(order(), UUID.randomUUID().toString(), user(UUID.randomUUID().toString()).roles("ADMIN"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));
        assertNoOrder();
    }

    @Test
    void givenACheckoutWithoutTheCsrfToken_whenItArrives_thenItIsForbidden() throws Exception {
        mvc.perform(guest(post(CHECKOUT)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
            .andExpect(status().isForbidden());
        assertNoOrder();
    }

    private void assertNoOrder() {
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isZero();
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isZero();
    }

    private ObjectNode order() {
        ObjectNode body = json.createObjectNode();
        body.putObject("buyer")
            .put("name", " Ana Pérez ")
            .put("email", "Ana@Example.com")
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
        return body;
    }

    private ResultActions checkout(ObjectNode body) throws Exception {
        return checkout(body, UUID.randomUUID().toString());
    }

    private ResultActions checkout(ObjectNode body, String idempotencyKey) throws Exception {
        return mvc.perform(guest(post(CHECKOUT)).with(csrf()).header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions checkoutRequest(ObjectNode body, String idempotencyKey,
                                          RequestPostProcessor as)
        throws Exception {
        return mvc.perform(guest(post(CHECKOUT)).with(csrf()).with(as).header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
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
