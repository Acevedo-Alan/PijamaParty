package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * UC-04 over real HTTP, as the back office sees it: the initial administrator is created at startup from the
 * settings below, then signs in with the session cookie and the CSRF token of the SPA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "store.admin.email= Owner@Example.com",
    "store.admin.name=Dueña",
    "store.admin.initial-password=" + AdministratorSignInApiTest.PASSWORD})
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdministratorSignInApiTest {

    static final String PASSWORD = "correct horse battery staple";
    private static final String LOGIN = "/api/admin/auth/login";
    private static final String ME = "/api/admin/auth/me";

    private final TestRestTemplate http;
    private final JdbcTemplate owner;

    AdministratorSignInApiTest(TestRestTemplate http, PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void unlockAndForgetSessions() {
        owner.update("update app_user set failed_login_attempts = 0, locked_until = null");
        owner.update("delete from spring_session");
    }

    @Test
    void givenTheDeploymentSettings_whenTheBackendStarted_thenTheSingleAdministratorExistsWithAHashedPassword() {
        var admin = owner.queryForMap("select email, name, role, password_hash from app_user");

        assertThat(admin).containsEntry("email", "owner@example.com").containsEntry("name", "Dueña")
            .containsEntry("role", "ADMIN");
        assertThat((String) admin.get("password_hash")).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void givenCorrectCredentials_whenSigningIn_thenTheAdministratorReachesThePanel() {
        var browser = new Browser();
        browser.get("/api/health");
        String csrfBefore = browser.cookie("XSRF-TOKEN");

        ResponseEntity<String> login = browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"email\":\"owner@example.com\"").contains("\"name\":\"Dueña\"");
        assertThat(browser.cookie("XSRF-TOKEN")).as("a new CSRF token in the sign-in response").isNotNull()
            .isNotEqualTo(csrfBefore);
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie ->
            assertThat(cookie).startsWith("SESSION=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax"));
        ResponseEntity<String> me = browser.get(ME);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("\"email\":\"owner@example.com\"");
    }

    @Test
    void givenWrongCredentials_whenTryingToSignIn_thenAGenericMessageDoesNotRevealWhichFieldFailed() {
        var browser = new Browser();
        browser.get("/api/health");

        ResponseEntity<String> wrongPassword = browser.post(LOGIN, credentials("owner@example.com", "wrong password!!"));
        ResponseEntity<String> unknownEmail = browser.post(LOGIN, credentials("nobody@example.com", PASSWORD));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getBody()).isEqualTo(wrongPassword.getBody());
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenFiveConsecutiveFailures_whenSigningInAgainEvenWithTheRightPassword_thenTheAccountIsLockedWithItsUnlockTime() {
        var browser = new Browser();
        browser.get("/api/health");
        ResponseEntity<String> last = null;
        for (int i = 0; i < 5; i++) {
            last = browser.post(LOGIN, credentials("owner@example.com", "wrong password!!"));
        }

        ResponseEntity<String> withRightPassword = browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(withRightPassword.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(withRightPassword.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Instant lockedUntil = owner.queryForObject("select locked_until from app_user", Instant.class);
        assertThat(withRightPassword.getBody()).contains("\"lockedUntil\":\"" + lockedUntil + "\"");
    }

    @Test
    void givenNoCsrfToken_whenSigningIn_thenItIsRejected() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.exchange(LOGIN, HttpMethod.POST,
            new HttpEntity<>(credentials("owner@example.com", PASSWORD), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void givenAnExistingSession_whenSigningIn_thenTheSessionIdChangesAndTheOldOneIsWorthless() {
        var browser = new Browser();
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));
        String sessionBefore = browser.cookie("SESSION");
        Map<String, String> cookiesBefore = Map.copyOf(browser.cookies);

        ResponseEntity<String> again = browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionBefore).isNotNull();
        assertThat(browser.cookie("SESSION")).isNotNull().isNotEqualTo(sessionBefore);
        assertThat(new Browser(cookiesBefore).get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenASignedInAdministrator_whenSigningOut_thenTheSessionNoLongerWorksAndTheCsrfTokenIsDropped() {
        var browser = new Browser();
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));
        Map<String, String> signedInCookies = Map.copyOf(browser.cookies);

        ResponseEntity<String> logout = browser.post("/api/admin/auth/logout", "");

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().get(HttpHeaders.SET_COOKIE))
            .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=;").contains("Max-Age=0"));
        assertThat(new Browser(signedInCookies).get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenASignedInAdministrator_whenItsSessionIsStored_thenItKeepsTheUserIdNotTheEmailAndExpiresAfter60Minutes() {
        var browser = new Browser();
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        var session = owner.queryForMap("select principal_name, max_inactive_interval from spring_session");
        UUID adminId = owner.queryForObject("select id from app_user", UUID.class);

        assertThat(session).containsEntry("principal_name", adminId.toString())
            .containsEntry("max_inactive_interval", 3600);
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Keeps cookies between requests and sends the CSRF token the way the SPA does (X-XSRF-TOKEN header). */
    private final class Browser {

        private final Map<String, String> cookies = new LinkedHashMap<>();

        Browser() {
        }

        Browser(Map<String, String> cookies) {
            this.cookies.putAll(cookies);
        }

        String cookie(String name) {
            return cookies.get(name);
        }

        ResponseEntity<String> get(String path) {
            return exchange(HttpMethod.GET, path, null);
        }

        ResponseEntity<String> post(String path, String json) {
            return exchange(HttpMethod.POST, path, json);
        }

        private ResponseEntity<String> exchange(HttpMethod method, String path, String json) {
            var headers = new HttpHeaders();
            if (!cookies.isEmpty()) {
                headers.add(HttpHeaders.COOKIE, String.join("; ",
                    cookies.entrySet().stream().map(cookie -> cookie.getKey() + "=" + cookie.getValue()).toList()));
            }
            if (method != HttpMethod.GET && cookies.containsKey("XSRF-TOKEN")) {
                headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(json, headers), String.class);
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
