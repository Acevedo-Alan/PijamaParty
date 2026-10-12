package com.pijamaparty.store.platform.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * T-E3-28: the back-office policy of RNF-22 as configured in application.yml, over real HTTP with a real session. The
 * administrator signs in (password and second factor, as in UC-04) and is limited to 300 requests per minute, counted
 * by user; without a signed-in session the route answers 401, never 429.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "store.rate-limit.enabled=true",
    "store.admin.email=owner@example.com",
    "store.admin.name=Dueña",
    "store.admin.initial-password=" + BackOfficeRateLimitTest.PASSWORD})
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BackOfficeRateLimitTest {

    static final String PASSWORD = "correct horse battery staple";
    private static final String SHIPPING = "/api/admin/shipping";
    private static final int CAPACITY = 300;

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final JdbcTemplate owner;

    BackOfficeRateLimitTest(TestRestTemplate http, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** The database is shared with other tests: the administrator starts unlocked and without a second factor. */
    @BeforeEach
    void unlockAndForgetSecondFactor() {
        owner.update("""
            update app_user set failed_login_attempts = 0, locked_until = null, totp_secret = null,
                                totp_enabled_at = null, totp_last_step = null
            """);
        owner.update("delete from admin_recovery_code");
        owner.update("delete from spring_session");
    }

    @Test
    void givenTheAdministratorUsedUpTheMinute_whenAnotherRequestArrives_thenItGets429WithRetryAfter() {
        var admin = signedInAdministrator();
        var anonymous = new Browser();
        anonymous.get("/api/health");

        // The bucket refills gradually (5 a second), so a fast client gets a few more than 300 before the first 429.
        int allowed = 0;
        ResponseEntity<String> limited = admin.get(SHIPPING);
        while (limited.getStatusCode() == HttpStatus.OK && allowed < 2 * CAPACITY) {
            allowed++;
            limited = admin.get(SHIPPING);
        }

        assertThat(allowed).isGreaterThanOrEqualTo(CAPACITY).isLessThan(2 * CAPACITY);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(anonymous.get(SHIPPING).getStatusCode()).as("not counted without a session")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Enrolls the second factor, which opens the session. None of these requests counts for the policy: the session
     * was not signed in yet when each one arrived.
     */
    private Browser signedInAdministrator() {
        var browser = new Browser();
        browser.get("/api/health");
        browser.post("/api/admin/auth/login", "{\"email\":\"owner@example.com\",\"password\":\"" + PASSWORD + "\"}");
        String secret = json(browser.get("/api/admin/auth/enrollment")).get("secret").asText();
        ResponseEntity<String> enrolled = browser.post("/api/admin/auth/enrollment",
            "{\"code\":\"" + appCode(secret) + "\"}");
        assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.OK);
        return browser;
    }

    /** What an authenticator app shows now. */
    private static String appCode(String secret) {
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret,
                Instant.now().getEpochSecond() / 30);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }

    /** Keeps cookies between requests and sends the CSRF token the way the SPA does (X-XSRF-TOKEN header). */
    private final class Browser {

        private final Map<String, String> cookies = new LinkedHashMap<>();

        ResponseEntity<String> get(String path) {
            return exchange(HttpMethod.GET, path, null);
        }

        ResponseEntity<String> post(String path, String body) {
            return exchange(HttpMethod.POST, path, body);
        }

        private ResponseEntity<String> exchange(HttpMethod method, String path, String body) {
            var headers = new HttpHeaders();
            if (!cookies.isEmpty()) {
                headers.add(HttpHeaders.COOKIE, String.join("; ",
                    cookies.entrySet().stream().map(cookie -> cookie.getKey() + "=" + cookie.getValue()).toList()));
            }
            if (method != HttpMethod.GET && cookies.containsKey("XSRF-TOKEN")) {
                headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
            for (String setCookie : response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
                String pair = setCookie.split(";", 2)[0];
                String name = pair.substring(0, pair.indexOf('='));
                String value = pair.substring(pair.indexOf('=') + 1);
                if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
            return response;
        }
    }
}
