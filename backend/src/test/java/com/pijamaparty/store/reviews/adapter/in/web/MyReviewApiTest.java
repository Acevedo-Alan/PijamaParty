package com.pijamaparty.store.reviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.platform.testing.Browser;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-24 (T-E5-06) over real HTTP, as the storefront sees it: a signed-in customer reviews a product it bought and edits
 * its review; UC-28: its reviews go with its account and are in its data download. Row Level Security, PostgreSQL,
 * CSRF, Spring Session and the account deletion listener are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MyReviewApiTest {

    private static final String REVIEWS = "/api/account/reviews/";
    private static final String LOGIN = "/api/account/login";
    private static final String PASSWORD = "ocho1234";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private UUID product;
    private UUID variant;

    MyReviewApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                    PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void theCatalogHasPijamaLuna() {
        UUID category = UUID.randomUUID();
        product = UUID.randomUUID();
        variant = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', 'verano', 0, true, now(), now())
            """, category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama Luna', 'pijama-luna', '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, 'PL-M-ROSA', 'M', 0, 'Rosa', 0, 1500000, true, now(), now())
            """, variant, product);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    // --- Acceptance criteria of UC-24 ---

    @Test
    void givenACustomerWhoDidNotBuyTheProduct_whenItTriesToReviewIt_thenItIsToldOnlyBuyersCan() {
        Browser bruno = signedIn(customer("bruno@example.com", "Bruno Díaz"));

        ResponseEntity<String> refused = bruno.put(REVIEWS + product, review(5, "Muy cómodo"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(refused).get("problem").asText()).isEqualTo("NOT_A_BUYER");
        assertThat(json(bruno.get(REVIEWS + product)).get("eligible").asBoolean()).isFalse();
        assertThat(count()).isZero();
    }

    @Test
    void givenABuyer_whenItReviewsTheProductAndEditsTheApprovedReview_thenEachSubmissionWaitsForModeration() {
        UUID ana = customer("ana@example.com", "Ana María Pérez");
        order(ana, "DELIVERED");
        Browser browser = signedIn(ana);
        JsonNode before = json(browser.get(REVIEWS + product));
        assertThat(before.get("eligible").asBoolean()).isTrue();
        assertThat(before.get("review").isNull()).isTrue();

        ResponseEntity<String> created = browser.put(REVIEWS + product, review(5, "  Muy cómodo "));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(created).get("status").asText()).isEqualTo("PENDING");
        assertThat(json(created).get("text").asText()).isEqualTo("Muy cómodo");
        assertThat(owner.queryForObject("select author_name from review", String.class)).isEqualTo("Ana");
        owner.update("update review set status = 'APPROVED', moderated_at = now()");

        ResponseEntity<String> edited = browser.put(REVIEWS + product, json(Map.of("rating", 3)));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mine = json(browser.get(REVIEWS + product)).get("review");
        assertThat(mine.get("rating").asInt()).isEqualTo(3);
        assertThat(mine.get("text").isNull()).isTrue();
        assertThat(mine.get("status").asText()).as("it leaves the product page").isEqualTo("PENDING");
        assertThat(count()).isOne();
    }

    @Test
    void givenACustomerWithAReview_whenItDeletesItsAccount_thenItsReviewsAreGoneAndWereInItsDataDownload() {
        UUID ana = customer("ana@example.com", "Ana Pérez");
        order(ana, "PAID");
        Browser browser = signedIn(ana);
        browser.put(REVIEWS + product, review(4, "Lindo"));

        JsonNode download = json(browser.get("/api/account/data"));
        assertThat(download.get("reviews")).singleElement().satisfies(review -> {
            assertThat(review.get("productName").asText()).isEqualTo("Pijama Luna");
            assertThat(review.get("rating").asInt()).isEqualTo(4);
            assertThat(review.get("status").asText()).isEqualTo("PENDING");
        });

        assertThat(browser.delete("/api/account", "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count()).isZero();
    }

    // --- Rules and errors ---

    @Test
    void givenFieldsThatBreakTheRules_whenSubmitted_thenTheInvalidOnesAreNamedAndNothingIsStored() {
        UUID ana = customer("ana@example.com", "Ana Pérez");
        order(ana, "PAID");
        Browser browser = signedIn(ana);

        ResponseEntity<String> invalid = browser.put(REVIEWS + product, json(Map.of("rating", 4.5,
            "text", "x".repeat(1_001))));

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(invalid).get("problem").asText()).isEqualTo("INVALID_REVIEW");
        assertThat(json(invalid).get("fields")).extracting(JsonNode::asText).containsExactly("rating", "text");
        assertThat(browser.put(REVIEWS + product, "{}").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(count()).isZero();
    }

    @Test
    void givenAnUnknownProduct_whenReviewed_thenItIsNotFound() {
        Browser browser = signedIn(customer("ana@example.com", "Ana Pérez"));

        ResponseEntity<String> unknown = browser.put(REVIEWS + UUID.randomUUID(), review(5, null));

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(unknown).get("problem").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(json(browser.get(REVIEWS + UUID.randomUUID())).get("eligible").asBoolean()).isFalse();
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItReviews_thenItIsUnauthorized() {
        Browser guest = new Browser(http);
        guest.get("/api/health");

        assertThat(guest.get(REVIEWS + product).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(guest.put(REVIEWS + product, review(5, null)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenASignedInBuyerWithoutTheCsrfToken_whenItReviews_thenItIsForbidden() {
        UUID ana = customer("ana@example.com", "Ana Pérez");
        order(ana, "PAID");
        Map<String, String> cookies = new HashMap<>(signedIn(ana).cookies());
        cookies.remove("XSRF-TOKEN");

        assertThat(new Browser(http, cookies).put(REVIEWS + product, review(5, null)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count()).isZero();
    }

    private UUID customer(String email, String name) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, ?, 'CUSTOMER', 0, now(), now())
            """, id, email, passwords.hash(PASSWORD).value(), name);
        return id;
    }

    private Browser signedIn(UUID customer) {
        String email = owner.queryForObject("select email from app_user where id = ?", String.class, customer);
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post(LOGIN, json(Map.of("email", email, "password", PASSWORD))).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        return browser;
    }

    /** An order of the customer with one Pijama Luna, in {@code status}. */
    private void order(UUID customer, String status) {
        UUID order = UUID.randomUUID();
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        boolean shipped = status.equals("SHIPPED") || status.equals("DELIVERED");
        owner.update("""
            insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name,
                                customer_last_name, customer_email,
                                customer_phone, national_id, street, street_number, city, province, postal_code,
                                subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                                terms_version, reservation_expires_at, shipment_tracking_number, delivered_at,
                                created_at, updated_at)
            values (?, nextval('order_number_seq'), ?, ?, ?, 'Ana', 'Pérez', 'ana@example.com', '3425551234',
                    '30123456', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 1500000, 0, 300000, 1800000,
                    'MERCADOPAGO', '2026-10', now() + interval '30 minutes', ?,
                    case when ? then now() end, now(), now())
            """, order, customer, HexFormat.of().formatHex(token), status, shipped ? "TN123456789AR" : null,
            status.equals("DELIVERED"));
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (?, ?, 0, ?, 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 1500000, 1)
            """, UUID.randomUUID(), order, variant);
    }

    private int count() {
        Integer reviews = owner.queryForObject("select count(*) from review", Integer.class);
        return reviews == null ? 0 : reviews;
    }

    private String review(int rating, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("text", text);
        return json(body);
    }

    private String json(Map<String, ?> body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
