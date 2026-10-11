package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.ResetAdministratorSecondFactor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
 * settings below, then signs in with the session cookie and the CSRF token of the SPA, in two steps: the password,
 * then the second factor of T-E1-16, computed here as an authenticator app would.
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
    private static final String SECOND_FACTOR = "/api/admin/auth/second-factor";
    private static final String ENROLLMENT = "/api/admin/auth/enrollment";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ResetAdministratorSecondFactor reset;
    private final JdbcTemplate owner;
    private List<String> recoveryCodes = List.of();

    AdministratorSignInApiTest(TestRestTemplate http, ObjectMapper json, ResetAdministratorSecondFactor reset,
                               PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.reset = reset;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void unlockAndForgetSessions() {
        owner.update("""
            update app_user set failed_login_attempts = 0, locked_until = null, totp_secret = null,
                                totp_enabled_at = null, totp_last_step = null
            """);
        owner.update("delete from admin_recovery_code");
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
    void givenNoSecondFactor_whenThePasswordIsRight_thenTheBackOfficeOnlyOpensAfterEnrollingWithCodesShownOnce() {
        var browser = new Browser();
        browser.get("/api/health");
        String csrfBefore = browser.cookie("XSRF-TOKEN");

        ResponseEntity<String> login = browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(login).get("nextStep").asText()).isEqualTo("ENROLLMENT");
        assertThat(browser.get(ME).getStatusCode()).as("the password alone opens nothing")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.post(SECOND_FACTOR, code("123456")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode enrollment = json(browser.get(ENROLLMENT));
        String secret = enrollment.get("secret").asText();
        assertThat(enrollment.get("otpauthUri").asText()).startsWith("otpauth://totp/Pijama%20Party:owner%40example.com?")
            .contains("secret=" + secret);
        assertThat(enrollment.get("qrCode").asText()).startsWith("data:image/png;base64,");
        assertThat(browser.post(ENROLLMENT, code("000000")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select failed_login_attempts from app_user", Integer.class)).isOne();

        ResponseEntity<String> enrolled = browser.post(ENROLLMENT, code(appCode(secret, 0)));

        assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json(enrolled);
        assertThat(body.at("/administrator/email").asText()).isEqualTo("owner@example.com");
        assertThat(body.get("recoveryCodes")).hasSize(10);
        assertThat(browser.cookie("XSRF-TOKEN")).as("a new CSRF token when the session opens").isNotNull()
            .isNotEqualTo(csrfBefore);
        assertThat(enrolled.getHeaders().get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie ->
            assertThat(cookie).startsWith("SESSION=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax"));
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] stored = owner.queryForObject("select totp_secret from app_user", byte[].class);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).as("encrypted at rest").doesNotContain(secret);
        assertThat(owner.queryForObject("select failed_login_attempts from app_user", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from admin_recovery_code", Integer.class)).isEqualTo(10);
    }

    @Test
    void givenCorrectCredentialsAndAValidCodeOfTheApp_whenSigningIn_thenTheAdministratorReachesThePanel() {
        String secret = enrolled();
        var browser = new Browser();
        browser.get("/api/health");

        ResponseEntity<String> login = browser.post(LOGIN, credentials("owner@example.com", PASSWORD));
        ResponseEntity<String> signedIn = browser.post(SECOND_FACTOR, code(appCode(secret, 1)));

        assertThat(json(login).get("nextStep").asText()).isEqualTo("SECOND_FACTOR");
        assertThat(signedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signedIn.getBody()).contains("\"email\":\"owner@example.com\"").contains("\"name\":\"Dueña\"");
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenTheRightPasswordAndAWrongOrUsedCode_whenSigningIn_thenTheBackOfficeStaysClosedAndItCountsAsAFailure() {
        String secret = enrolled();
        String used = appCode(secret, 1);
        signIn(new Browser(), used);
        var browser = new Browser();
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));

        ResponseEntity<String> wrong = browser.post(SECOND_FACTOR, code("000000"));
        ResponseEntity<String> reused = browser.post(SECOND_FACTOR, code(used));

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reused.getBody()).isEqualTo(wrong.getBody());
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select failed_login_attempts from app_user", Integer.class)).isEqualTo(2);
    }

    @Test
    void givenAnUnusedRecoveryCode_whenUsedInsteadOfACode_thenTheAdministratorGetsInOnceWithIt() {
        enrolled();
        String recovery = recoveryCodes.getFirst();

        var first = new Browser();
        ResponseEntity<String> withCode = signIn(first, recovery.toLowerCase().replace("-", " "));
        var second = new Browser();
        ResponseEntity<String> again = signIn(second, recovery);

        assertThat(withCode.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(owner.queryForObject("select count(*) from admin_recovery_code where used_at is null",
            Integer.class)).isEqualTo(9);
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
        String secret = enrolled();
        var browser = new Browser();
        signIn(browser, appCode(secret, 1));
        String sessionBefore = browser.cookie("SESSION");
        Map<String, String> cookiesBefore = Map.copyOf(browser.cookies);

        ResponseEntity<String> again = signIn(browser, recoveryCodes.getFirst());

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionBefore).isNotNull();
        assertThat(browser.cookie("SESSION")).isNotNull().isNotEqualTo(sessionBefore);
        assertThat(new Browser(cookiesBefore).get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenASignedInAdministrator_whenSigningOut_thenTheSessionNoLongerWorksAndTheCsrfTokenIsDropped() {
        String secret = enrolled();
        var browser = new Browser();
        signIn(browser, appCode(secret, 1));
        Map<String, String> signedInCookies = Map.copyOf(browser.cookies);

        ResponseEntity<String> logout = browser.post("/api/admin/auth/logout", "");

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().get(HttpHeaders.SET_COOKIE))
            .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=;").contains("Max-Age=0"));
        assertThat(new Browser(signedInCookies).get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenASignedInAdministrator_whenItsSessionIsStored_thenItKeepsTheUserIdNotTheEmailAndExpiresAfter60Minutes() {
        String secret = enrolled();
        owner.update("delete from spring_session");
        signIn(new Browser(), appCode(secret, 1));

        var session = owner.queryForMap("select principal_name, max_inactive_interval from spring_session "
            + "where principal_name is not null");
        UUID adminId = owner.queryForObject("select id from app_user", UUID.class);

        assertThat(session).containsEntry("principal_name", adminId.toString())
            .containsEntry("max_inactive_interval", 3600);
    }

    @Test
    void givenALostPhoneAndLostCodes_whenTheSecondFactorIsReset_thenTheNextSignInEnrollsAgain() {
        enrolled();

        assertThat(CurrentActor.callAsSystem(reset::execute))
            .isEqualTo(new ResetAdministratorSecondFactor.Result.Reset());

        var browser = new Browser();
        browser.get("/api/health");
        assertThat(json(browser.post(LOGIN, credentials("owner@example.com", PASSWORD))).get("nextStep").asText())
            .isEqualTo("ENROLLMENT");
        assertThat(owner.queryForObject("select count(*) from admin_recovery_code", Integer.class)).isZero();
    }

    /** Enrolls the administrator's authenticator app through the API; keeps the codes; returns the secret. */
    private String enrolled() {
        var browser = new Browser();
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));
        String secret = json(browser.get(ENROLLMENT)).get("secret").asText();
        JsonNode body = json(browser.post(ENROLLMENT, code(appCode(secret, 0))));
        recoveryCodes = new ArrayList<>();
        body.get("recoveryCodes").forEach(code -> recoveryCodes.add(code.asText()));
        return secret;
    }

    /** Password, then {@code secondFactor}: a code of the app or a recovery code. */
    private ResponseEntity<String> signIn(Browser browser, String secondFactor) {
        browser.get("/api/health");
        browser.post(LOGIN, credentials("owner@example.com", PASSWORD));
        return browser.post(SECOND_FACTOR, code(secondFactor));
    }

    /**
     * What an authenticator app shows now, {@code offset} steps ahead (within the one step of tolerance): the
     * enrollment spends the current step, so later sign-ins in the same test use the next one.
     */
    private static String appCode(String secret, int offset) {
        long step = Instant.now().getEpochSecond() / 30 + offset;
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, step);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String code(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
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
