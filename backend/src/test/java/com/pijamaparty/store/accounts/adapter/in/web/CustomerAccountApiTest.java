package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.Browser;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-19 (T-E4-02) over real HTTP, as the storefront sees it: a visitor registers, gets the verification email (the
 * email job's use case sends it to the Resend stub), opens its link and signs in with the session cookie and the CSRF
 * token of the SPA. Row Level Security and PostgreSQL are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerAccountApiTest {

    private static final String REGISTER = "/api/account/register";
    private static final String VERIFY = "/api/account/verify-email";
    private static final String LOGIN = "/api/account/login";
    private static final String LOGOUT = "/api/account/logout";
    private static final String ME = "/api/account/me";
    private static final String PASSWORD = "ocho1234";
    private static final Pattern LINK = Pattern.compile("/cuenta/verificar#([A-Za-z0-9_-]{43})");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private Browser browser;

    CustomerAccountApiTest(TestRestTemplate http, ObjectMapper json, ResendStub resend, SendNextEmail sendNextEmail,
                           PasswordHasher passwords, PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.resend = resend;
        this.sendNextEmail = sendNextEmail;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void aBrowserWithItsCsrfToken() {
        browser = new Browser(http);
        browser.get("/api/health");
    }

    @AfterEach
    void cleanUp() {
        resend.reset();
        owner.execute("truncate email_outbox, address, user_token, external_identity, admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenAVisitor_whenItRegistersVerifiesItsEmailAndSignsIn_thenItActsAsThatCustomerUntilItSignsOut() {
        assertThat(register(" Ana@Example.com ", PASSWORD, " Ana Pérez ").getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        Map<String, Object> account = owner.queryForMap(
            "select id, email, name, role, email_verified, password_hash from app_user");
        assertThat(account).containsEntry("email", "ana@example.com").containsEntry("name", "Ana Pérez")
            .containsEntry("role", "CUSTOMER").containsEntry("email_verified", false);
        assertThat((String) account.get("password_hash")).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(browser.get(ME).getStatusCode()).as("registering does not sign in")
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = linkOf(sendDueEmails().getFirst());
        assertThat(owner.queryForObject("select token_hash from user_token", String.class))
            .isEqualTo(new AccountToken(token).hash());

        assertThat(browser.post(VERIFY, body("token", token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.queryForObject("select email_verified from app_user", Boolean.class)).isTrue();
        assertThat(browser.get(ME).getStatusCode()).as("verifying does not sign in")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        String csrfBefore = browser.cookie("XSRF-TOKEN");

        ResponseEntity<String> login = signIn("ANA@example.com", PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(login).get("email").asText()).isEqualTo("ana@example.com");
        assertThat(browser.cookie("XSRF-TOKEN")).as("a new CSRF token when the session opens").isNotNull()
            .isNotEqualTo(csrfBefore);
        JsonNode me = json(browser.get(ME));
        assertThat(me.get("id").asText()).isEqualTo(account.get("id").toString());
        assertThat(me.get("name").asText()).isEqualTo("Ana Pérez");
        assertThat(owner.queryForList("select principal_name from spring_session where principal_name is not null",
            String.class)).as("the session names the user by its id, never by its email (RNF-27)")
            .containsExactly(account.get("id").toString());
        assertThat(browser.get("/api/admin/auth/me").getStatusCode()).as("a customer never reaches the back office")
            .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(browser.post(LOGOUT, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenAnEmailAlreadyRegistered_whenSomeoneRegistersWithIt_thenItIsRefusedWithAMessageThatExposesNoAccountData() {
        register("ana@example.com", PASSWORD, "Ana Pérez");

        ResponseEntity<String> again = register("ANA@example.com", "otra-clave-1", "Otra persona");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(again).get("problem").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(again.getBody()).doesNotContain("Ana", "ana@example.com", "Pérez");
        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isOne();
        assertThat(owner.queryForObject("select count(*) from email_outbox", Integer.class)).isOne();
    }

    @Test
    void givenInvalidFields_whenRegistering_thenTheReasonIsGivenAndNothingIsCreated() {
        assertThat(json(register("ana@example.com", "siete77", "Ana")).get("problem").asText())
            .isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(json(register("not an email", PASSWORD, "Ana")).get("problem").asText())
            .isEqualTo("INVALID_EMAIL");
        assertThat(register("ana@example.com", PASSWORD, " ").getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(owner.queryForObject("select count(*) from app_user", Integer.class)).isZero();
    }

    @Test
    void givenAnUnverifiedAccountWhoseEmailWasSent_whenItSignsInWithTheRightPassword_thenItIsRefusedAndANewLinkSent() {
        register("ana@example.com", PASSWORD, "Ana");
        String first = linkOf(sendDueEmails().getFirst());

        ResponseEntity<String> login = signIn("ana@example.com", PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(login).get("problem").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(browser.get(ME).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signIn("ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        List<ResendStub.Request> resent = sendDueEmails();
        assertThat(resent).as("one new email while one is waiting").hasSize(2);
        String second = linkOf(resent.getLast());
        assertThat(second).isNotEqualTo(first);

        assertThat(browser.post(VERIFY, body("token", first)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(signIn("ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenALinkAlreadyUsedExpiredOrMadeUp_whenItIsOpened_thenItIsRefused() {
        register("ana@example.com", PASSWORD, "Ana");
        String token = linkOf(sendDueEmails().getFirst());
        browser.post(VERIFY, body("token", token));
        String expired = "e".repeat(43);
        Instant sentAt = Instant.now().minus(Duration.ofHours(25));
        owner.update("""
            insert into user_token (id, user_id, purpose, token_hash, created_at, expires_at)
            select ?, id, 'EMAIL_VERIFICATION', ?, ?, ? from app_user
            """, UUID.randomUUID(), new AccountToken(expired).hash(), Timestamp.from(sentAt),
            Timestamp.from(sentAt.plus(Duration.ofHours(24))));

        for (String link : List.of(token, expired, "f".repeat(43), "not a token")) {
            ResponseEntity<String> opened = browser.post(VERIFY, body("token", link));
            assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(json(opened).get("problem").asText()).isEqualTo("INVALID_LINK");
        }
    }

    @Test
    void givenFiveWrongPasswords_whenSigningIn_thenTheAccountIsLockedAndSaysUntilWhen() {
        register("ana@example.com", PASSWORD, "Ana");
        for (int i = 0; i < 4; i++) {
            assertThat(signIn("ana@example.com", "wrong-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> locked = signIn("ana@example.com", "wrong-password");

        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(json(locked).get("lockedUntil").asText()).isNotBlank();
        assertThat(signIn("ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void givenTheAdministratorsCredentials_whenSigningInAsACustomer_thenTheyAreInvalid() {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'owner@example.com', true, ?, 'Owner', 'ADMIN', 0, now(), now())
            """, UUID.randomUUID(), passwords.hash("correct horse battery").value());

        ResponseEntity<String> login = signIn("owner@example.com", "correct horse battery");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(register("owner@example.com", PASSWORD, "Ana")).get("problem").asText())
            .isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    private ResponseEntity<String> register(String email, String password, String name) {
        return browser.post(REGISTER, json(Map.of("email", email, "password", password, "name", name)));
    }

    private ResponseEntity<String> signIn(String email, String password) {
        return browser.post(LOGIN, json(Map.of("email", email, "password", password)));
    }

    private String body(String field, String value) {
        return json(Map.of(field, value));
    }

    /** Sends every email due, as the job does, and answers the ones delivered so far. */
    private List<ResendStub.Request> sendDueEmails() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
        return resend.delivered();
    }

    private static String linkOf(ResendStub.Request email) {
        Matcher link = LINK.matcher(email.json().get("text").asText());
        assertThat(link.find()).as("the email carries the verification link").isTrue();
        return link.group(1);
    }

    private String json(Map<String, String> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
