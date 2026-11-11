package com.pijamaparty.store.accounts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.PasswordHasher;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.platform.testing.Browser;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * UC-40 (T-E4-18) over real HTTP: the signed-in customer's phone, with Spring Security, the CSRF token, Spring
 * Session, Row Level Security and PostgreSQL real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChangePhoneApiTest {

    private static final String PHONE = "/api/account/phone";
    private static final String ME = "/api/account/me";
    private static final String LOGIN = "/api/account/login";
    private static final String PASSWORD = "ocho1234";

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final PasswordHasher passwords;
    private final JdbcTemplate owner;

    ChangePhoneApiTest(TestRestTemplate http, ObjectMapper json, PasswordHasher passwords,
                       PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.passwords = passwords;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, email_outbox, address, user_token, external_identity, pending_link, "
            + "admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenACustomerWithoutAPhone_whenItSaves342_400_0000_thenItsAccountAndMeHaveTheDigits() {
        Browser ana = signedIn("ana@example.com");

        ResponseEntity<String> saved = ana.put(PHONE, "{\"phone\":\"342 400-0000\"}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(saved).get("phone").asText()).isEqualTo("3424000000");
        assertThat(owner.queryForObject("select phone from app_user where email = 'ana@example.com'", String.class))
            .isEqualTo("3424000000");
        // What the checkout prefills (UC-21) and the account pages show.
        assertThat(json(ana.get(ME)).get("phone").asText()).isEqualTo("3424000000");
    }

    @Test
    void givenACustomer_whenItSaves123_thenItIsUnprocessableAndNothingChanges() {
        Browser ana = signedIn("ana@example.com");
        owner.update("update app_user set phone = '3425551234' where email = 'ana@example.com'");

        ResponseEntity<String> refused = ana.put(PHONE, "{\"phone\":\"123\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(refused).get("problem").asText()).isEqualTo("INVALID_PHONE");
        assertThat(refused.getBody()).doesNotContain("123\"");
        assertThat(owner.queryForObject("select phone from app_user where email = 'ana@example.com'", String.class))
            .isEqualTo("3425551234");
    }

    @Test
    void givenTwoCustomers_whenOneSavesItsPhone_thenOnlyItsOwnAccountChanges() {
        Browser ana = signedIn("ana@example.com");
        signedIn("bea@example.com");

        ana.put(PHONE, "{\"phone\":\"3424000000\"}");

        assertThat(owner.queryForObject("select phone from app_user where email = 'bea@example.com'", String.class))
            .isNull();
    }

    @Test
    void givenAVisitorWhoIsNotSignedIn_whenItSendsAPhone_thenItIsRefused() {
        Browser visitor = new Browser(http);
        visitor.get("/api/health");

        ResponseEntity<String> refused = visitor.put(PHONE, "{\"phone\":\"3424000000\"}");

        assertThat(refused.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(owner.queryForObject("select count(*) from app_user where phone is not null", Integer.class))
            .isZero();
    }

    @Test
    void givenNoPhoneInTheBody_whenTheCustomerSendsIt_thenItIsABadRequest() {
        Browser ana = signedIn("ana@example.com");

        assertThat(ana.put(PHONE, "{}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Browser signedIn(String email) {
        owner.update("""
            insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                  created_at, updated_at)
            values (?, ?, true, ?, 'Ana Pérez', 'CUSTOMER', 0, now(), now())
            """, UUID.randomUUID(), email, passwords.hash(PASSWORD).value());
        Browser browser = new Browser(http);
        browser.get("/api/health");
        assertThat(browser.post(LOGIN, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        return browser;
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
