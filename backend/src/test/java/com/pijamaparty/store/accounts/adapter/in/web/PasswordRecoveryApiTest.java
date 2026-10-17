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
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.Browser;
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
 * UC-19 (T-E4-03) over real HTTP, as the storefront sees it: a customer who forgot its password asks for a link, gets
 * the reset email (the email job's use case sends it to the Resend stub), opens it with a new password and signs in
 * with that one. Row Level Security, PostgreSQL and Spring Session are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PasswordRecoveryApiTest {

    private static final String REGISTER = "/api/account/register";
    private static final String VERIFY = "/api/account/verify-email";
    private static final String LOGIN = "/api/account/login";
    private static final String ME = "/api/account/me";
    private static final String RECOVERY = "/api/account/password-recovery";
    private static final String RESET = "/api/account/password-reset";
    private static final String PASSWORD = "ocho1234";
    private static final String NEW_PASSWORD = "nueva-clave";
    private static final Pattern VERIFICATION_LINK = Pattern.compile("/cuenta/verificar#([A-Za-z0-9_-]{43})");
    private static final Pattern RESET_LINK = Pattern.compile("/cuenta/restablecer#([A-Za-z0-9_-]{43})");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;
    private Browser browser;

    PasswordRecoveryApiTest(TestRestTemplate http, ObjectMapper json, ResendStub resend, SendNextEmail sendNextEmail,
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
        browser = newBrowser();
    }

    @AfterEach
    void cleanUp() {
        resend.reset();
        owner.execute("truncate email_outbox, address, user_token, external_identity, admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenACustomerSignedInElsewhere_whenItResetsItsPassword_thenOnlyTheNewOneWorksAndItsSessionsEnd() {
        register("ana@example.com");
        Browser phone = newBrowser();
        phone.post(VERIFY, body("token", link(VERIFICATION_LINK, sendDueEmails().getFirst())));
        assertThat(signIn(phone, "ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(browser.post(RECOVERY, body("email", " ANA@example.com ")).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        String token = link(RESET_LINK, sendDueEmails().getLast());

        assertThat(browser.post(RESET, reset(token, NEW_PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(browser.get(ME).getStatusCode()).as("resetting does not sign in")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(phone.get(ME).getStatusCode()).as("every session of the account ended")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signIn(browser, "ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signIn(browser, "ana@example.com", NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenARecoveryLinkAlreadyUsed_whenItIsOpenedAgain_thenItIsRefused() {
        register("ana@example.com");
        browser.post(RECOVERY, body("email", "ana@example.com"));
        String token = link(RESET_LINK, sentResetEmail());
        browser.post(RESET, reset(token, NEW_PASSWORD));

        ResponseEntity<String> again = browser.post(RESET, reset(token, "otra-clave-2"));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(json(again).get("problem").asText()).isEqualTo("INVALID_LINK");
        assertThat(signIn(browser, "ana@example.com", NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void givenAnUnknownEmailOrTheAdministrators_whenAskingForALink_thenTheAnswerIsTheSameAndNoEmailIsQueued() {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, 'owner@example.com', true, ?, 'Owner', 'ADMIN', 0, now(), now())
            """, UUID.randomUUID(), passwords.hash("correct horse battery").value());

        for (String email : List.of("nadie@example.com", "owner@example.com", "not an email")) {
            ResponseEntity<String> answer = browser.post(RECOVERY, body("email", email));
            assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(answer.getBody()).isNull();
        }

        assertThat(owner.queryForObject("select count(*) from email_outbox", Integer.class)).isZero();
    }

    @Test
    void givenATooShortPassword_whenResetting_thenTheReasonIsGivenAndTheLinkStillWorks() {
        register("ana@example.com");
        browser.post(RECOVERY, body("email", "ana@example.com"));
        String token = link(RESET_LINK, sentResetEmail());

        ResponseEntity<String> refused = browser.post(RESET, reset(token, "siete77"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(refused).get("problem").asText()).isEqualTo("PASSWORD_TOO_SHORT");
        assertThat(browser.post(RESET, reset(token, NEW_PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void givenALockedCustomerThatNeverVerifiedItsEmail_whenItResetsItsPassword_thenItSignsInAtOnce() {
        register("ana@example.com");
        for (int i = 0; i < 5; i++) {
            signIn(browser, "ana@example.com", "wrong-password");
        }
        assertThat(signIn(browser, "ana@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        browser.post(RECOVERY, body("email", "ana@example.com"));

        browser.post(RESET, reset(link(RESET_LINK, sentResetEmail()), NEW_PASSWORD));

        assertThat(owner.queryForObject("select email_verified from app_user", Boolean.class)).isTrue();
        assertThat(signIn(browser, "ana@example.com", NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Browser newBrowser() {
        Browser fresh = new Browser(http);
        fresh.get("/api/health");
        return fresh;
    }

    private void register(String email) {
        assertThat(browser.post(REGISTER, json(Map.of("email", email, "password", PASSWORD, "name", "Ana Pérez")))
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> signIn(Browser from, String email, String password) {
        return from.post(LOGIN, json(Map.of("email", email, "password", password)));
    }

    private String body(String field, String value) {
        return json(Map.of(field, value));
    }

    private String reset(String token, String password) {
        return json(Map.of("token", token, "password", password));
    }

    /** Sends every email due, as the job does, and answers the ones delivered so far. */
    private List<ResendStub.Request> sendDueEmails() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
        return resend.delivered();
    }

    private ResendStub.Request sentResetEmail() {
        return sendDueEmails().stream()
            .filter(email -> RESET_LINK.matcher(email.json().get("text").asText()).find())
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("no reset email was sent"));
    }

    private static String link(Pattern pattern, ResendStub.Request email) {
        Matcher link = pattern.matcher(email.json().get("text").asText());
        assertThat(link.find()).as("the email carries the link").isTrue();
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
