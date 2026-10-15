package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStub;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStubConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-14 (T-E3b-05): "Pagar" on the tracking link of a MODO order, through the API with the real security filter chain,
 * Row Level Security and PostgreSQL, against the MODO stub. The order is placed with MODO through the cart and checkout
 * API, so its payment request is a real attempt of the store.
 */
@ApiTest
@Import(ModoStubConfiguration.class)
class ModoRetryPaymentApiTest {

    private static final String CART_COOKIE = "__Host-cart_token";
    private static final String PAYMENT_REQUESTS = "/v2/payment-requests/";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final ModoStub modo;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();

    ModoRetryPaymentApiTest(MockMvc mvc, ObjectMapper json, ModoStub modo, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.modo = modo;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** 2 pijamas at ARS 20,000 in the guest's cart, 5 in stock; shipping to Santa Fe ARS 4,500. */
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
        modo.reset();
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, email_outbox, "
            + "withdrawal_request, order_tracking_token, order_item, orders, cart_item, cart, coupon_redemption, coupon, "
            + "stock_movement, stock_level, product_image, variant, product, category, shipping_zone_rate");
    }

    @Test
    void givenAModoPaymentRequestThatExpiredWhileTheReservationLasts_whenPagarIsTapped_thenANewOneForTheSameAmount()
        throws Exception {
        JsonNode placed = placeOrder();
        long number = placed.get("number").asLong();
        String firstUrl = placed.get("paymentUrl").asText();
        owner.update("""
            update payment_checkout set expires_at = now() - interval '1 minute'
            where payment_id = (select id from payment where order_number = ? and provider = 'MODO')
            """, number);

        String body = mvc.perform(post("/api/orders/{token}/retry-payment", placed.get("trackingToken").asText())
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String paymentUrl = json.readTree(body).get("paymentUrl").asText();

        List<JsonNode> paymentRequests = modo.requestsTo(PAYMENT_REQUESTS).stream()
            .map(request -> readTree(request.body())).toList();
        assertThat(paymentRequests).hasSize(2);
        assertThat(paymentRequests).extracting(request -> request.get("external_intention_id").asText())
            .containsExactly(number + "-1", number + "-2");
        assertThat(paymentRequests).extracting(request -> request.get("amount").decimalValue())
            .usingElementComparator(BigDecimal::compareTo)
            .containsOnly(BigDecimal.valueOf(placed.get("totalCents").asLong(), 2));

        List<String> attempts = owner.queryForList("""
            select c.checkout_id from payment_checkout c join payment p on p.id = c.payment_id
            where p.order_number = ? and p.provider = 'MODO' order by c.attempt
            """, String.class, number);
        assertThat(attempts).hasSize(2).doesNotHaveDuplicates();
        assertThat(paymentUrl).isNotEqualTo(firstUrl).contains("id=" + attempts.get(1));
        assertThat(owner.queryForMap("""
            select p.checkout_id, p.checkout_url, p.amount_cents, p.status from payment p
            where p.order_number = ? and p.provider = 'MODO'
            """, number))
            .containsEntry("checkout_id", attempts.get(1))
            .containsEntry("checkout_url", paymentUrl)
            .containsEntry("amount_cents", placed.get("totalCents").asLong())
            .containsEntry("status", "PENDING");
        assertThat(owner.queryForObject("select status from orders where number = ?", String.class, number))
            .isEqualTo("AWAITING_PAYMENT");
    }

    private JsonNode placeOrder() throws Exception {
        String body = mvc.perform(post("/api/checkout").cookie(new Cookie(CART_COOKIE, cartToken)).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
