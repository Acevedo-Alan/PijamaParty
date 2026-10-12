package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import java.util.List;
import java.util.function.Supplier;
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
import org.springframework.test.context.TestConstructor;

/**
 * The RNF-22 policies as configured in application.yml: login per IP (UC-04) and per account (RNF-29.5), catalog and
 * search (UC-26) of T-E1-15, cart changes (UC-08, UC-09) of T-E2-04, and coupon attempts (UC-10) of T-E2-11. The
 * test client (127.0.0.1) plays Caddy, so {@code X-Forwarded-For} is the client IP and each test uses its own
 * addresses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "store.rate-limit.enabled=true")
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RateLimitPoliciesTest {

    private static final String LOGIN = "/api/admin/auth/login";

    private final TestRestTemplate http;
    private String csrfToken;

    RateLimitPoliciesTest(TestRestTemplate http) {
        this.http = http;
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
    void givenOneHundredTwentyCatalogRequestsInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429() {
        for (int i = 0; i < 120; i++) {
            assertThat(get("/api/catalog/products/no-such-product", "198.51.100.10").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertLimited(firstOfNextRequests(() -> get("/api/catalog/filters", "198.51.100.10")));
        assertThat(get("/api/catalog/filters", "198.51.100.11").getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private static void assertLimited(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
    }

    private ResponseEntity<String> signIn(String clientIp, String email) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"not the password\"}";
        return http.exchange(LOGIN, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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

    private ResponseEntity<String> get(String path, String clientIp) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIp);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
