package com.pijamaparty.store.ordering.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
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
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-12 and UC-13: the shipping cost of step 1 of the checkout through the API, with the real security filter chain,
 * Row Level Security and PostgreSQL. The guest fills its cart through the cart API.
 */
@ApiTest
class CheckoutApiTest {

    private static final String SHIPPING_COST = "/api/checkout/shipping-cost";
    private static final String CART_COOKIE = "__Host-cart_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final String token = newToken();
    private UUID variant;

    CheckoutApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A pijama at ARS 20,000 with 5 units; shipping to Santa Fe ARS 4,500 and to Córdoba ARS 6,500. */
    @BeforeEach
    void setUp() {
        variant = sellable(2_000_000, 5);
        rate("SANTA_FE", 450_000);
        rate("CORDOBA", 650_000);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, stock_level, "
            + "product_image, variant, product, size_guide, category, shipping_zone_rate");
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
    }

    /** T-E3b-04: MODO switched on is in {@link PaymentProvidersApiTest}. */
    @Test
    void givenTheDefaultConfiguration_whenAVisitorAsksThePaymentProviders_thenOnlyMercadoPagoIsOffered()
        throws Exception {
        mvc.perform(get("/api/checkout/payment-providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.providers.length()").value(1))
            .andExpect(jsonPath("$.providers[0]").value("MERCADOPAGO"));
    }

    @Test
    void givenACartWithACoupon_whenAskingTheShippingToAProvince_thenTheServerReturnsEveryAmountOfTheSummary()
        throws Exception {
        addToCart(2);
        owner.update("""
            insert into coupon (id, code, type, discount_value, active, created_at, updated_at)
            values (?, 'VERANO10', 'PERCENTAGE', 10, true, now(), now())
            """, UUID.randomUUID());
        mvc.perform(guest(post("/api/cart/coupon")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", "VERANO10")))).andExpect(status().isOk());

        shippingCost("SANTA_FE")
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.province").value("SANTA_FE"))
            .andExpect(jsonPath("$.subtotalCents").value(4_000_000))
            .andExpect(jsonPath("$.discountCents").value(400_000))
            .andExpect(jsonPath("$.shippingCents").value(450_000))
            .andExpect(jsonPath("$.totalCents").value(4_050_000));
    }

    @Test
    void givenABuyerWhoGoesBackAStep_whenTheProvinceChanges_thenTheShippingCostAndTheTotalAreRecalculated()
        throws Exception {
        addToCart(1);

        shippingCost("SANTA_FE")
            .andExpect(jsonPath("$.shippingCents").value(450_000))
            .andExpect(jsonPath("$.totalCents").value(2_450_000));
        shippingCost("CORDOBA")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.province").value("CORDOBA"))
            .andExpect(jsonPath("$.shippingCents").value(650_000))
            .andExpect(jsonPath("$.totalCents").value(2_650_000));
    }

    @Test
    void givenFreeShippingFrom50000_whenTheSubtotalAfterTheDiscountIs50000OrMore_thenTheShippingCostIs0()
        throws Exception {
        owner.update("update shop_setting set free_shipping_threshold_cents = 5000000");
        addToCart(2);

        shippingCost("SANTA_FE").andExpect(jsonPath("$.shippingCents").value(450_000));

        addToCart(1);

        shippingCost("SANTA_FE")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(6_000_000))
            .andExpect(jsonPath("$.shippingCents").value(0))
            .andExpect(jsonPath("$.totalCents").value(6_000_000));
    }

    @Test
    void givenAProvinceWithoutACost_whenTheBuyerPicksIt_thenItIsToldThereIsNoShippingToThatZone() throws Exception {
        addToCart(1);

        shippingCost("MENDOZA")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("NO_SHIPPING"))
            .andExpect(jsonPath("$.province").value("MENDOZA"));
    }

    @Test
    void givenNoCartOrACartWithoutAvailableItems_whenAskingTheShipping_thenTheCartIsEmpty() throws Exception {
        shippingCost("SANTA_FE")
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));
        mvc.perform(get(SHIPPING_COST).param("province", "SANTA_FE")).andExpect(status().isConflict());

        addToCart(1);
        owner.update("update stock_level set on_hand = 0");

        shippingCost("SANTA_FE").andExpect(status().isConflict()).andExpect(jsonPath("$.problem").value("EMPTY_CART"));
    }

    @Test
    void givenTheAdministrator_whenAskingTheShipping_thenItHasNoCartToBuy() throws Exception {
        addToCart(1);

        mvc.perform(guest(get(SHIPPING_COST)).param("province", "SANTA_FE")
                .with(user(UUID.randomUUID().toString()).roles("ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.problem").value("EMPTY_CART"));
    }

    @Test
    void givenNoProvinceOrAnUnknownOne_whenAskingTheShipping_thenItIsABadRequest() throws Exception {
        addToCart(1);

        mvc.perform(guest(get(SHIPPING_COST)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        shippingCost("ATLANTIDA")
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions shippingCost(String province) throws Exception {
        return mvc.perform(guest(get(SHIPPING_COST)).param("province", province));
    }

    private void addToCart(int quantity) throws Exception {
        mvc.perform(guest(post("/api/cart/items")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", variant, "quantity", quantity))))
            .andExpect(status().isOk());
    }

    private void rate(String province, long costCents) {
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values (?, ?, now())",
            province, costCents);
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
        return request.cookie(new Cookie(CART_COOKIE, token));
    }

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
