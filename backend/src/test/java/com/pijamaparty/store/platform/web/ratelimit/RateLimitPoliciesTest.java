package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The RNF-22 policies as configured in application.yml: login per IP (UC-04) and per account (RNF-29.5), catalog and
 * search (UC-26) of T-E1-15, cart changes (UC-08, UC-09) of T-E2-04, coupon attempts (UC-10) of T-E2-11, the
 * tracking link and its payment retry (UC-14) of T-E3-08, checkout confirmations (UC-13) of T-E3-19, email
 * corrections (UC-17) of T-E3-23, withdrawal requests (UC-29) of T-E3-27, the customers' sign-in, registration
 * and password recovery (UC-19) of T-E4-10, the account deletion (UC-28) of T-E4-16, and the shipping costs and payment methods (RNF-31.6) of T-E5-12. The test client (127.0.0.1)
 * plays Caddy, so {@code X-Forwarded-For} is the client IP and each test uses its own addresses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "store.rate-limit.enabled=true")
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RateLimitPoliciesTest {

    private static final String LOGIN = "/api/admin/auth/login";
    private static final String CUSTOMER_LOGIN = "/api/account/login";

    private final TestRestTemplate http;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private String csrfToken;

    RateLimitPoliciesTest(TestRestTemplate http, PasswordHasher passwords, PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUpCustomers() {
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @BeforeEach
    void fetchCsrfToken() {
        List<String> cookies = http.getForEntity("/api/health", String.class).getHeaders()
            .getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        csrfToken = cookies.stream().filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
            .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'))).findFirst().orElseThrow();
    }

    @Test
    void givenFiveSignInAttemptsInAMinuteFromOneIp_whenAnotherArrives_thenItGets429WithRetryAfter() {
        for (int i = 1; i <= 5; i++) {
            assertThat(signIn("192.0.2.10", "nobody" + i + "@example.com").getStatusCode())
                .as("attempt %d reaches the sign-in, body intact", i).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertLimited(signIn("192.0.2.10", "someone-else@example.com"));
        assertThat(signIn("192.0.2.11", "someone-else@example.com").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenFiveSignInAttemptsInAMinuteForOneAccountFromManyIps_whenAnotherArrives_thenItGets429() {
        for (int i = 1; i <= 5; i++) {
            String sameAccount = i % 2 == 0 ? "Target@Example.com" : " target@example.com ";
            assertThat(signIn("192.0.2." + (20 + i), sameAccount).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertLimited(signIn("192.0.2.30", "TARGET@example.com"));
        assertThat(signIn("192.0.2.30", "another@example.com").getStatusCode())
            .as("the limit is per account, so another account from that IP is served")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenFiveSignInAttemptsInAMinuteFromOneIpOnBothSignIns_whenACustomerTriesAgain_thenItGets429WithRetryAfter() {
        for (int i = 1; i <= 5; i++) {
            String path = i <= 2 ? LOGIN : CUSTOMER_LOGIN;
            assertThat(signIn(path, "192.0.2.40", "nobody" + i + "@example.com").getStatusCode())
                .as("attempt %d on %s reaches the sign-in", i, path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertLimited(signIn(CUSTOMER_LOGIN, "192.0.2.40", "someone-else@example.com"));
        assertLimited(signIn(LOGIN, "192.0.2.40", "someone-else@example.com"));
        assertThat(signIn(CUSTOMER_LOGIN, "192.0.2.41", "someone-else@example.com").getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenFiveCustomerSignInAttemptsInAMinuteForOneAccountFromManyIps_whenAnotherArrives_thenItGets429() {
        for (int i = 1; i <= 5; i++) {
            String sameAccount = i % 2 == 0 ? "Victim@Example.com" : " victim@example.com ";
            assertThat(signIn(CUSTOMER_LOGIN, "192.0.2." + (50 + i), sameAccount).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertLimited(signIn(CUSTOMER_LOGIN, "192.0.2.60", "VICTIM@example.com"));
        assertThat(signIn(CUSTOMER_LOGIN, "192.0.2.60", "another-one@example.com").getStatusCode())
            .as("the limit is per account, so another account from that IP is served")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenThreeRegistrationsAndRecoveriesInAnHourFromOneIp_whenAnotherArrives_thenItGets429WithRetryAfter() {
        assertThat(register("192.0.2.70").getStatusCode())
            .as("an invalid email reaches the registration, which creates nothing")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(recoverPassword("192.0.2.70").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(register("192.0.2.70").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        for (ResponseEntity<String> limited : List.of(recoverPassword("192.0.2.70"), register("192.0.2.70"))) {
            assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .as("3 per hour: one more every 20 minutes").isBetween(60L, 1200L);
        }
        assertThat(register("192.0.2.71").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(recoverPassword("192.0.2.71").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void givenOneHundredTwentyCatalogRequestsInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429() {
        for (int i = 0; i < 120; i++) {
            assertThat(get("/api/catalog/products/no-such-product", "198.51.100.10").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertLimited(firstOfNextRequests(() -> get("/api/catalog/filters", "198.51.100.10")));
        assertThat(get("/api/catalog/filters", "198.51.100.11").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenOneHundredTwentyPurchaseInfoReadsInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429() {
        for (int i = 0; i < 120; i++) {
            assertThat(get("/api/shipping/rates", "198.51.100.30").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertLimited(firstOfNextRequests(() -> get("/api/checkout/payment-providers", "198.51.100.30")));
        assertLimited(get("/api/shipping/rates", "198.51.100.30"));
        assertThat(get("/api/shipping/rates", "198.51.100.31").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenSixtySearchesInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429ButBrowsingGoesOn() {
        for (int i = 0; i < 60; i++) {
            assertThat(get("/api/catalog/products?q=pijama", "198.51.100.20").getStatusCode())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        assertLimited(firstOfNextRequests(() -> get("/api/catalog/suggestions?q=pi", "198.51.100.20")));
        assertThat(get("/api/catalog/products", "198.51.100.20").getStatusCode())
            .as("browsing without text only counts for the catalog policy").isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenSixtyCartChangesInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429ButReadingTheCartGoesOn() {
        for (int i = 0; i < 60; i++) {
            assertThat(addToCart("203.0.113.10").getStatusCode())
                .as("an unknown variant reaches the cart, which answers 404").isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertLimited(firstOfNextRequests(() -> addToCart("203.0.113.10")));
        assertThat(get("/api/cart", "203.0.113.10").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addToCart("203.0.113.11").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenTenCouponAttemptsInAMinuteOnTheSameCart_whenAnotherArrives_thenItGets429WithRetryAfter() {
        String cart = "c".repeat(43);
        for (int i = 0; i < 10; i++) {
            assertThat(applyCoupon("203.0.113.20", cart).getStatusCode())
                .as("a token nobody issued finds no cart, which answers 409").isEqualTo(HttpStatus.CONFLICT);
        }

        assertLimited(applyCoupon("203.0.113.20", cart));
        assertThat(applyCoupon("203.0.113.20", "d".repeat(43)).getStatusCode())
            .as("another cart from that IP has its own bucket").isEqualTo(HttpStatus.CONFLICT);
        assertThat(applyCoupon("203.0.113.21", cart).getStatusCode())
            .as("the bucket is per cart and IP together").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void givenSixtyCouponRequestsInAMinuteWithoutACartFromOneIp_whenMoreArrive_thenTheCartPolicyLimitsThem() {
        for (int i = 0; i < 60; i++) {
            assertThat(applyCoupon("203.0.113.30", null).getStatusCode())
                .as("without a cookie only the cart-changes policy counts").isEqualTo(HttpStatus.FORBIDDEN);
        }

        assertLimited(firstOfNextRequests(() -> applyCoupon("203.0.113.30", null)));
    }

    @Test
    void givenFiveCheckoutAttemptsInAMinuteFromTheSameCartAndIp_whenAnotherArrives_thenItGets429WithRetryAfter() {
        String cart = "k".repeat(43);
        for (int i = 0; i < 5; i++) {
            assertThat(checkout("203.0.113.60", cart).getStatusCode())
                .as("an empty form reaches the checkout, which refuses it").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertLimited(checkout("203.0.113.60", cart));
        assertThat(checkout("203.0.113.60", "m".repeat(43)).getStatusCode())
            .as("another cart from that IP has its own bucket").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(checkout("203.0.113.61", cart).getStatusCode())
            .as("the bucket is per cart and IP together").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void givenThirtyTrackingLinkRequestsInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429() {
        for (int i = 0; i < 30; i++) {
            assertThat(get("/api/orders/" + "t".repeat(43), "203.0.113.40").getStatusCode())
                .as("a token nobody issued finds no order").isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertLimited(firstOfNextRequests(() -> get("/api/orders/" + "u".repeat(43), "203.0.113.40")));
        assertThat(get("/api/orders/" + "t".repeat(43), "203.0.113.41").getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenFivePaymentRetriesInAMinuteOfTheSameOrder_whenAnotherArrives_thenItGets429WithRetryAfter() {
        String order = "r".repeat(43);
        for (int i = 0; i < 5; i++) {
            assertThat(retryPayment("203.0.113.50", order).getStatusCode())
                .as("a token nobody issued finds no order").isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertLimited(retryPayment("203.0.113.50", order));
        assertThat(retryPayment("203.0.113.50", "s".repeat(43)).getStatusCode())
            .as("another order from that IP has its own bucket").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(retryPayment("203.0.113.51", order).getStatusCode())
            .as("the bucket is per order and IP together").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenThreeEmailCorrectionsInAnHourOfTheSameOrder_whenAnotherArrives_thenItGets429WithRetryAfter() {
        String order = "e".repeat(43);
        for (int i = 0; i < 3; i++) {
            assertThat(correctEmail("203.0.113.70", order).getStatusCode())
                .as("a token nobody issued finds no order").isEqualTo(HttpStatus.NOT_FOUND);
        }

        ResponseEntity<String> limited = correctEmail("203.0.113.70", order);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
            .as("3 per hour: one more every 20 minutes").isBetween(60L, 1200L);
        assertThat(correctEmail("203.0.113.70", "f".repeat(43)).getStatusCode())
            .as("another order from that IP has its own bucket").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(correctEmail("203.0.113.71", order).getStatusCode())
            .as("the bucket is per order and IP together").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void givenThreeWithdrawalRequestsInAnHourOfTheSameOrderAndIp_whenAnotherArrives_thenItGets429WithRetryAfter() {
        for (String number : List.of("99999901", "99999901", "#99999901")) {
            assertThat(requestWithdrawal("203.0.113.80", number).getStatusCode())
                .as("%s: a number nobody ordered matches no order", number).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        ResponseEntity<String> limited = requestWithdrawal("203.0.113.80", "99999901");
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
            .as("3 per hour: one more every 20 minutes").isBetween(60L, 1200L);
        assertThat(requestWithdrawal("203.0.113.80", "99999902").getStatusCode())
            .as("another order from that IP has its own bucket").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(requestWithdrawal("203.0.113.81", "99999901").getStatusCode())
            .as("the bucket is per order and IP together").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * The buckets refill continuously (Bucket4j greedy refill: 120 per minute is one token every 0.5 s), so the
     * requests above give back a few tokens while they run. The limit must show within a handful more.
     */
    private static ResponseEntity<String> firstOfNextRequests(Supplier<ResponseEntity<String>> request) {
        ResponseEntity<String> response = request.get();
        for (int i = 0; i < 10 && response.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS; i++) {
            response = request.get();
        }
        return response;
    }

    @Test
    void givenFiveAccountDeletionsInAMinuteFromOneIp_whenAnotherArrives_thenItGets429WithRetryAfter() {
        for (int i = 1; i <= 5; i++) {
            assertThat(deleteAccount("192.0.2.80").getStatusCode())
                .as("attempt %d reaches the endpoint", i).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertLimited(deleteAccount("192.0.2.80"));
        assertThat(deleteAccount("192.0.2.81").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenFiveWrongDeletionPasswordsOfOneCustomerFromManyIps_whenAnotherArrives_thenItGets429WithRetryAfter() {
        Map<String, String> session = signedIn("ana-deletes@example.com", "192.0.2.90");
        for (int i = 1; i <= 5; i++) {
            assertThat(asCustomer(HttpMethod.DELETE, "/api/account", "192.0.2." + (100 + i), session,
                "{\"password\":\"not the password\"}").getStatusCode())
                .as("attempt %d reaches the endpoint", i).isEqualTo(HttpStatus.FORBIDDEN);
        }

        // 5 every 15 minutes: one token every 3 minutes.
        assertLimited(asCustomer(HttpMethod.DELETE, "/api/account", "192.0.2.120", session,
            "{\"password\":\"not the password\"}"), 180);
    }

    @Test
    void givenFiveDataDownloadsInAMinuteOfOneCustomer_whenAnotherArrives_thenItGets429WithRetryAfter() {
        Map<String, String> session = signedIn("ana-downloads@example.com", "192.0.2.91");
        for (int i = 1; i <= 5; i++) {
            assertThat(asCustomer(HttpMethod.GET, "/api/account/data", "192.0.2." + (130 + i), session, null)
                .getStatusCode()).as("download %d", i).isEqualTo(HttpStatus.OK);
        }

        assertLimited(asCustomer(HttpMethod.GET, "/api/account/data", "192.0.2.150", session, null));
    }

    /** A verified customer with a password, signed in from {@code clientIp}: its session and CSRF cookies. */
    private Map<String, String> signedIn(String email, String clientIp) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana', 'CUSTOMER', 0, now(), now())
            """, UUID.randomUUID(), email, passwords.hash("ocho1234").value());
        ResponseEntity<String> login = post(CUSTOMER_LOGIN, clientIp,
            "{\"email\":\"" + email + "\",\"password\":\"ocho1234\"}");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> cookies = new HashMap<>();
        for (String setCookie : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            String pair = setCookie.split(";", 2)[0];
            cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        }
        return cookies;
    }

    private ResponseEntity<String> asCustomer(HttpMethod method, String path, String clientIp,
                                              Map<String, String> session, String body) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        // The sign-in may hand a new CSRF token; otherwise the one of this test still holds.
        String csrf = session.getOrDefault("XSRF-TOKEN", csrfToken);
        headers.add(HttpHeaders.COOKIE, "SESSION=" + session.get("SESSION") + "; XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    /** UC-28 (T-E4-16): without a session, so it never deletes anything; the limit comes first anyway. */
    private ResponseEntity<String> deleteAccount(String clientIp) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/account", HttpMethod.DELETE,
            new HttpEntity<>("{\"password\":\"not the password\"}", headers), String.class);
    }

    private static void assertLimited(ResponseEntity<String> response) {
        assertLimited(response, 60);
    }

    /** {@code maxRetryAfter}: the seconds until the next token, at most the period over the capacity. */
    private static void assertLimited(ResponseEntity<String> response, long maxRetryAfter) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, maxRetryAfter);
    }

    private ResponseEntity<String> signIn(String clientIp, String email) {
        return signIn(LOGIN, clientIp, email);
    }

    private ResponseEntity<String> signIn(String path, String clientIp, String email) {
        return post(path, clientIp, "{\"email\":\"" + email + "\",\"password\":\"not the password\"}");
    }

    private ResponseEntity<String> register(String clientIp) {
        return post("/api/account/register", clientIp,
            "{\"email\":\"not-an-email\",\"password\":\"ocho1234\",\"name\":\"Ana\"}");
    }

    /** An email nobody registered: the answer is the same and no email is queued. */
    private ResponseEntity<String> recoverPassword(String clientIp) {
        return post("/api/account/password-recovery", clientIp, "{\"email\":\"nobody@example.com\"}");
    }

    private ResponseEntity<String> post(String path, String clientIp, String body) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> addToCart(String clientIp) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"variantId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":1}";
        return http.exchange("/api/cart/items", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    /** A guest's cart cookie when {@code cartToken} is not null; the CSRF cookie always. */
    private ResponseEntity<String> applyCoupon(String clientIp, String cartToken) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken
            + (cartToken == null ? "" : "; __Host-cart_token=" + cartToken));
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/cart/coupon", HttpMethod.POST, new HttpEntity<>("{\"code\":\"PIJAMA10\"}", headers),
            String.class);
    }

    /** A guest's cart cookie, a fresh Idempotency-Key and an empty form. */
    private ResponseEntity<String> checkout(String clientIp, String cartToken) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken + "; __Host-cart_token=" + cartToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.add("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/checkout", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
    }

    private ResponseEntity<String> retryPayment(String clientIp, String trackingToken) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        return http.exchange("/api/orders/" + trackingToken + "/retry-payment", HttpMethod.POST,
            new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> correctEmail(String clientIp, String trackingToken) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/orders/" + trackingToken + "/email", HttpMethod.PATCH,
            new HttpEntity<>("{\"email\":\"ana.perez@gmail.com\"}", headers), String.class);
    }

    private ResponseEntity<String> requestWithdrawal(String clientIp, String orderNumber) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"orderNumber\":\"" + orderNumber + "\",\"email\":\"ana@example.com\"}";
        return http.exchange("/api/withdrawals", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String clientIp) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
