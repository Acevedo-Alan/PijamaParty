package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.mock.web.MockHttpServletResponse;
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
    private final MercadoPagoStub mercadoPago;
    private final JdbcTemplate owner;
    private final String cartToken = newToken();
    private UUID pijama;

    PlaceOrderApiTest(MockMvc mvc, ObjectMapper json, MercadoPagoStub mercadoPago, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.mercadoPago = mercadoPago;
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
        owner.execute("truncate idempotency_key, payment_event, payment_checkout, payment, withdrawal_request, order_tracking_token, order_item, orders, "
            + "cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, stock_level, "
            + "product_image, variant, product, size_guide, category, shipping_zone_rate");
    }

    @Test
    void givenAPlacedOrder_whenItsPaymentIsCreated_thenTheBuyerIsSentToMercadoPagoWithTheExactServerAmount()
        throws Exception {
        String body = checkout(order()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentUrl").value(startsWith(MercadoPagoStub.INIT_POINT)))
            .andReturn().getResponse().getContentAsString();

        JsonNode placed = json.readTree(body);
        JsonNode preference = mercadoPago.lastRequest().json();
        assertThat(preference.get("items")).hasSize(1);
        assertThat(preference.get("items").get(0).get("unit_price").decimalValue().movePointRight(2).longValueExact())
            .isEqualTo(placed.get("totalCents").asLong());
        assertThat(preference.get("external_reference").asText()).isEqualTo(placed.get("number").asText());
        assertThat(mercadoPago.lastRequest().body()).doesNotContain("12345678", "3425551234", "San Mart",
            placed.get("trackingToken").asText());
        var payment = owner.queryForMap("select p.* from payment p join orders o on o.id = p.order_id "
            + "where o.number = ?", placed.get("number").asLong());
        assertThat(payment).containsEntry("status", "PENDING").containsEntry("amount_cents", 4_450_000L)
            .containsEntry("checkout_url", placed.get("paymentUrl").asText());
    }

    @Test
    void givenMercadoPagoDown_whenTheBuyerConfirms_thenTheOrderIsPlacedWithoutAPaymentUrlAndItsStockStaysReserved()
        throws Exception {
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");

        String body = checkout(order()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.paymentUrl").value(nullValue()))
            .andReturn().getResponse().getContentAsString();

        long number = json.readTree(body).get("number").asLong();
        assertThat(owner.queryForObject("select status from orders where number = ?", String.class, number))
            .isEqualTo("AWAITING_PAYMENT");
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isEqualTo(2);
        var payment = owner.queryForMap("select p.* from payment p join orders o on o.id = p.order_id "
            + "where o.number = ?", number);
        assertThat(payment).containsEntry("status", "PENDING").containsEntry("checkout_url", null);
    }

    @Test
    void givenACheckoutRefused_whenItIsAnswered_thenMercadoPagoIsNotCalled() throws Exception {
        checkout(order().put("acceptedTerms", false)).andExpect(status().isUnprocessableEntity());

        assertThat(mercadoPago.requests()).isEmpty();
        assertThat(owner.queryForObject("select count(*) from payment", Long.class)).isZero();
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
        assertThat(order.get("customer_first_name")).isEqualTo("Ana");
        assertThat(order.get("customer_last_name")).isEqualTo("Pérez");
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

    /**
     * T-E3-14 (RNF-20.2): a double tap or a network retry. The repetition has a tracking token of its own (only hashes
     * are stored, RNF-29.6), and both open the same order.
     */
    @Test
    void givenAPlacedOrder_whenTheSameKeyArrivesAgainWithTheSameBody_thenTheSameOrderIsAnsweredAndNothingIsRepeated()
        throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode first = json.readTree(checkout(order(), key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());

        JsonNode second = json.readTree(checkout(order(), key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());

        assertThat(second.get("number")).isEqualTo(first.get("number"));
        assertThat(second.get("status")).isEqualTo(first.get("status"));
        assertThat(second.get("totalCents")).isEqualTo(first.get("totalCents"));
        assertThat(second.get("reservationExpiresAt")).isEqualTo(first.get("reservationExpiresAt"));
        assertThat(second.get("paymentUrl")).isEqualTo(first.get("paymentUrl"));
        assertThat(second.get("trackingToken").asText()).hasSize(43)
            .isNotEqualTo(first.get("trackingToken").asText());
        for (JsonNode answer : List.of(first, second)) {
            mvc.perform(get("/api/orders/" + answer.get("trackingToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(first.get("number").asLong()));
        }
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isEqualTo(2);
        assertThat(owner.queryForObject("select count(*) from stock_movement where type = 'RESERVATION'",
            Integer.class)).isOne();
        assertThat(owner.queryForObject("select count(*) from payment", Integer.class)).isOne();
        assertThat(mercadoPago.requests()).hasSize(1);
        var key24h = owner.queryForMap("select * from idempotency_key");
        assertThat(key24h.get("scope")).isEqualTo("CART:" + new CartToken(cartToken).hash());
        assertThat(key24h.get("response_body").toString()).doesNotContain(first.get("trackingToken").asText(),
            "12345678", "ana@example.com");
    }

    @Test
    void givenAPlacedOrderWhoseProviderWasDown_whenTheSameKeyArrivesAgain_thenThePaymentIsOpenedNow()
        throws Exception {
        String key = UUID.randomUUID().toString();
        mercadoPago.answer(500, "{\"message\":\"internal_error\"}");
        checkout(order(), key).andExpect(status().isCreated()).andExpect(jsonPath("$.paymentUrl").value(nullValue()));
        mercadoPago.reset();

        checkout(order(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentUrl").value(startsWith(MercadoPagoStub.INIT_POINT)));

        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
    }

    @Test
    void givenAPlacedOrder_whenTheSameKeyArrivesWithADifferentBody_thenItIsRejectedWith422AndNothingHappens()
        throws Exception {
        String key = UUID.randomUUID().toString();
        checkout(order(), key).andExpect(status().isCreated());
        ObjectNode otherBuyer = order();
        ((ObjectNode) otherBuyer.get("buyer")).put("email", "otra@example.com");

        checkout(otherBuyer, key)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
        assertThat(owner.queryForObject("select count(*) from order_tracking_token", Integer.class)).isZero();
        assertThat(mercadoPago.requests()).hasSize(1);
    }

    @Test
    void givenACheckoutRefused_whenTheSameKeyArrivesWithTheDataCorrected_thenTheOrderIsPlaced() throws Exception {
        String key = UUID.randomUUID().toString();
        checkout(order().put("acceptedTerms", false), key).andExpect(status().isUnprocessableEntity());

        checkout(order(), key).andExpect(status().isCreated());

        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
    }

    /**
     * T-E3-14 (RNF-20.1, RNF-20.2): the loser either ran while the winner held the key, and is told to retry, or after
     * it committed, and gets its answer.
     */
    @Test
    void givenTwoRequestsWithTheSameKey_whenTheyArriveAtOnce_thenOneOrderIsPlacedAndTheOtherGetsItOrIsToldToRetry()
        throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<MockHttpServletResponse> answers = new ArrayList<>();
        try {
            List<Future<MockHttpServletResponse>> pending = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                pending.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return checkout(order(), key).andReturn().getResponse();
                }));
            }
            start.countDown();
            for (Future<MockHttpServletResponse> answer : pending) {
                answers.add(answer.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(answers).extracting(MockHttpServletResponse::getStatus).contains(201)
            .allMatch(code -> code == 201 || code == 409);
        long number = owner.queryForObject("select number from orders", Long.class);
        for (MockHttpServletResponse answer : answers) {
            JsonNode body = json.readTree(answer.getContentAsString());
            if (answer.getStatus() == 201) {
                assertThat(body.get("number").asLong()).isEqualTo(number);
            } else {
                assertThat(body.get("problem").asText()).isEqualTo("IDEMPOTENCY_KEY_IN_USE");
                assertThat(answer.getHeader("Retry-After")).isEqualTo("1");
            }
        }
        assertThat(owner.queryForObject("select reserved from stock_level where variant_id = ?", Integer.class,
            pijama)).isEqualTo(2);
        assertThat(owner.queryForObject("select count(*) from idempotency_key", Integer.class)).isOne();

        checkout(order(), key).andExpect(status().isCreated()).andExpect(jsonPath("$.number").value(number));
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
    }

    /**
     * T-E3-12. The loser is refused by the conditional reservation when both passed the cart check, naming the variant;
     * when it read its cart after the winner committed, the cart is not ready instead. Both show the cart notice.
     */
    @Test
    void givenTheLastUnitInTwoCarts_whenBothBuyersConfirmAtOnce_thenOnlyOneGetsItAndTheOtherIsTold() throws Exception {
        owner.update("update cart_item set quantity = 1");
        owner.update("update stock_level set on_hand = 1 where variant_id = ?", pijama);
        String otherCart = newToken();
        mvc.perform(post("/api/cart/items").cookie(new Cookie(CART_COOKIE, otherCart)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantId", pijama, "quantity", 1))))
            .andExpect(status().isOk());
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<MockHttpServletResponse> answers = new ArrayList<>();
        try {
            List<Future<MockHttpServletResponse>> pending = new ArrayList<>();
            for (String token : List.of(cartToken, otherCart)) {
                pending.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post(CHECKOUT).cookie(new Cookie(CART_COOKIE, token)).with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(order())))
                        .andReturn().getResponse();
                }));
            }
            start.countDown();
            for (Future<MockHttpServletResponse> answer : pending) {
                answers.add(answer.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(answers).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        MockHttpServletResponse refused = answers.stream().filter(answer -> answer.getStatus() == 409).findFirst()
            .orElseThrow();
        JsonNode problem = json.readTree(refused.getContentAsString());
        assertThat(problem.get("problem").asText()).isIn("INSUFFICIENT_STOCK", "CART_NOT_READY");
        if (problem.get("problem").asText().equals("INSUFFICIENT_STOCK")) {
            assertThat(problem.get("variantId").asText()).isEqualTo(pijama.toString());
            assertThat(problem.get("available").asInt()).isZero();
        }
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isOne();
        assertThat(owner.queryForMap("select on_hand, reserved from stock_level where variant_id = ?", pijama))
            .containsEntry("on_hand", 1).containsEntry("reserved", 1);
        assertThat(owner.queryForObject("select count(*) from stock_movement where type = 'RESERVATION'",
            Integer.class)).isOne();
        assertThat(mercadoPago.requests()).hasSize(1);
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
        ObjectNode withoutLastName = order();
        ((ObjectNode) withoutLastName.get("buyer")).remove("lastName");
        ObjectNode unknownProvince = order();
        ((ObjectNode) unknownProvince.get("address")).put("province", "ATLANTIDA");
        ObjectNode unknownProvider = order();
        unknownProvider.put("paymentProvider", "PAYPAL");
        ObjectNode tooLong = order();
        ((ObjectNode) tooLong.get("address")).put("street", "s".repeat(301));

        for (ObjectNode body : new ObjectNode[] {withoutLastName, unknownProvince, unknownProvider, tooLong}) {
            checkout(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertNoOrder();
    }

    @Test
    void givenMODOSwitchedOff_whenTheBuyerChoosesIt_thenItIsRejectedWith400AndNothingHappens() throws Exception {
        checkout(order().put("paymentProvider", "MODO"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("PAYMENT_PROVIDER_NOT_ENABLED"))
            .andExpect(jsonPath("$.paymentProvider").value("MODO"));
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

    // T-E7-10 (D-13): each part of the name, 1 to 50 characters once trimmed.
    @Test
    void givenAnEmptyFirstNameAndALastNameOver50Characters_whenConfirming_thenEachIsNamedWith422() throws Exception {
        ObjectNode body = order();
        ((ObjectNode) body.get("buyer")).put("firstName", "  ").put("lastName", "p".repeat(51));

        checkout(body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("INVALID_BUYER_DATA"))
            .andExpect(jsonPath("$.fields.length()").value(2))
            .andExpect(jsonPath("$.fields[0]").value("firstName"))
            .andExpect(jsonPath("$.fields[1]").value("lastName"));
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
            .put("firstName", " Ana ")
            .put("lastName", " Pérez")
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
