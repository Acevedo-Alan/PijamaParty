package com.pijamaparty.store.reviews.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.platform.testing.Browser;
import java.sql.Timestamp;
import java.time.Instant;
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
 * UC-24 (T-E5-07) over real HTTP, as the product page sees it: anyone reads the approved reviews of a listed product
 * with their average and count. Row Level Security and PostgreSQL are real; the reviews are stored as the owner, in the
 * status each test needs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductReviewsApiTest {

    private static final String LUNA_REVIEWS = "/api/catalog/products/pijama-luna/reviews";
    private static final String PASSWORD = "ocho1234";
    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private UUID product;

    ProductReviewsApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                          PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void theCatalogListsPijamaLuna() {
        UUID category = UUID.randomUUID();
        product = UUID.randomUUID();
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
            """, UUID.randomUUID(), product);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    // --- Acceptance criteria of UC-24 and UC-25 ---

    @Test
    void givenApprovedReviewsOf4And5_whenAVisitorOpensTheProductPage_thenTheAverageIs4Point5AndTheCountIsShown() {
        stored(UUID.randomUUID(), "Ana", 4, "Lindo", "APPROVED", T0);
        stored(UUID.randomUUID(), "Bruno", 5, null, "APPROVED", T0.plusSeconds(60));

        ResponseEntity<String> page = http.getForEntity(LUNA_REVIEWS, String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode body = json(page);
        assertThat(body.get("average").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(body.get("count").asLong()).isEqualTo(2);
        assertThat(body.get("reviews")).hasSize(2);
        JsonNode newest = body.get("reviews").get(0);
        assertThat(newest.get("authorName").asText()).isEqualTo("Bruno");
        assertThat(newest.get("rating").asInt()).isEqualTo(5);
        assertThat(newest.get("text").isNull()).isTrue();
        assertThat(Instant.parse(newest.get("createdAt").asText())).isEqualTo(T0.plusSeconds(60));
        assertThat(body.get("reviews").get(1).get("text").asText()).isEqualTo("Lindo");
        assertThat(newest.has("userId") || newest.has("status")).as("nothing that identifies the author").isFalse();
    }

    @Test
    void givenAReviewJustSubmittedAndARejectedOne_whenTheProductPageIsOpened_thenNeitherShowsNorCounts() {
        stored(UUID.randomUUID(), "Ana", 5, "Muy cómodo", "PENDING", T0);
        stored(UUID.randomUUID(), "Bruno", 1, "Feo", "REJECTED", T0);

        JsonNode body = json(http.getForEntity(LUNA_REVIEWS, String.class));

        assertThat(body.get("average").isNull()).isTrue();
        assertThat(body.get("count").asLong()).isZero();
        assertThat(body.get("reviews")).isEmpty();
    }

    @Test
    void givenACustomerWithAnApprovedReview_whenItDeletesItsAccount_thenItsReviewNoLongerShowsOrCounts() {
        UUID ana = customer("ana@example.com", "Ana Pérez");
        stored(ana, "Ana", 5, "Muy cómodo", "APPROVED", T0);
        stored(UUID.randomUUID(), "Bruno", 3, null, "APPROVED", T0);
        assertThat(json(http.getForEntity(LUNA_REVIEWS, String.class)).get("count").asLong()).isEqualTo(2);

        Browser browser = signedIn("ana@example.com");
        assertThat(browser.delete("/api/account", "{\"password\":\"" + PASSWORD + "\"}").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode body = json(http.getForEntity(LUNA_REVIEWS, String.class));
        assertThat(body.get("count").asLong()).isOne();
        assertThat(body.get("average").decimalValue()).isEqualByComparingTo("3.0");
    }

    // --- Pages and errors ---

    @Test
    void givenMoreApprovedReviewsThanAPage_whenTheNextPageIsRead_thenItHasTheOlderOnes() {
        for (int i = 0; i < 12; i++) {
            stored(UUID.randomUUID(), "Cliente" + i, 5, "Opinión " + i, "APPROVED", T0.plusSeconds(i));
        }

        JsonNode first = json(http.getForEntity(LUNA_REVIEWS, String.class));
        JsonNode second = json(http.getForEntity(LUNA_REVIEWS + "?page=1", String.class));

        assertThat(first.get("reviews")).hasSize(10);
        assertThat(first.get("reviews").get(0).get("text").asText()).isEqualTo("Opinión 11");
        assertThat(second.get("count").asLong()).isEqualTo(12);
        assertThat(second.get("reviews")).extracting(review -> review.get("text").asText())
            .containsExactly("Opinión 1", "Opinión 0");
    }

    @Test
    void givenAnUnknownOrHiddenProduct_whenItsReviewsAreRead_thenItIsNotFound() {
        ResponseEntity<String> unknown = http.getForEntity("/api/catalog/products/pijama-nube/reviews", String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        owner.update("update product set active = false where id = ?", product);
        assertThat(http.getForEntity(LUNA_REVIEWS, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenAPageOutOfRange_whenRead_thenItIsABadRequest() {
        assertThat(http.getForEntity(LUNA_REVIEWS + "?page=-1", String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity(LUNA_REVIEWS + "?page=1001", String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** A review of Pijama Luna by {@code author}, in {@code status}. */
    private void stored(UUID author, String authorName, int rating, String text, String status, Instant createdAt) {
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                                moderated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, case when ? = 'PENDING' then null else now() end)
            """, UUID.randomUUID(), product, author, rating, text, authorName, status,
            Timestamp.from(createdAt), Timestamp.from(createdAt), status);
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

    private Browser signedIn(String email) {
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post("/api/account/login", json(Map.of("email", email, "password", PASSWORD)))
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        return browser;
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
