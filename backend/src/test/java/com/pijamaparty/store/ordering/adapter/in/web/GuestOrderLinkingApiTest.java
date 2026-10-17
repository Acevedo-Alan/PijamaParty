package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStubConfiguration;
import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.Browser;
import java.time.Instant;
import java.util.ArrayList;
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
 * UC-22 (T-E4-08) over real HTTP: a buyer who ordered as a guest registers with the same email, verifies it and signs
 * in, and finds its guest orders in "Mis pedidos". The session, Row Level Security, the update guard of
 * {@code orders} (V33) and PostgreSQL are real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ResendStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuestOrderLinkingApiTest {

    private static final String ORDERS = "/api/account/orders";
    private static final String PASSWORD = "ocho1234";
    private static final Pattern LINK = Pattern.compile("/cuenta/verificar#([A-Za-z0-9_-]{43})");

    private final TestRestTemplate http;
    private final ObjectMapper json;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final JdbcTemplate owner;
    private Browser browser;

    GuestOrderLinkingApiTest(TestRestTemplate http, ObjectMapper json, ResendStub resend, SendNextEmail sendNextEmail,
                             PostgreSQLContainer<?> postgres) {
        this.http = http;
        this.json = json;
        this.resend = resend;
        this.sendNextEmail = sendNextEmail;
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
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders, email_outbox, address, "
            + "user_token, external_identity, pending_link, admin_recovery_code, app_user");
        owner.update("delete from spring_session");
    }

    @Test
    void givenAGuestOrderWithTheSameVerifiedEmail_whenTheCustomerRegisters_thenTheOrderShowsInItsList() {
        OrderId guests = guestOrder("ana@example.com");
        guestOrder("bruno@example.com");
        OrderId unlinked = guestOrder("ana@example.com");
        owner.update("update orders set unlinked_at = now() where id = ?", unlinked.value());

        register();
        assertThat(signIn().getStatusCode()).as("an unverified account does not sign in, and links nothing")
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(owner.queryForObject("select count(*) from orders where user_id is not null", Integer.class))
            .isZero();
        assertThat(browser.post("/api/account/verify-email", json(Map.of("token", verificationToken())))
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(signIn().getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(orderIds(browser.get(ORDERS)))
            .as("the order of another email and the one unlinked by an account deletion stay out")
            .containsExactly(guests.value().toString());
    }

    @Test
    void givenAGuestOrderPlacedAfterTheAccountWasVerified_whenTheCustomerSignsInAgain_thenItJoinsItsList() {
        register();
        browser.post("/api/account/verify-email", json(Map.of("token", verificationToken())));
        signIn();
        assertThat(orderIds(browser.get(ORDERS))).isEmpty();
        assertThat(browser.post("/api/account/logout", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        browser.get("/api/health");

        OrderId later = guestOrder("ana@example.com");
        assertThat(signIn().getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(orderIds(browser.get(ORDERS))).containsExactly(later.value().toString());
    }

    private OrderId guestOrder(String email) {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now());
        owner.update("update orders set customer_email = ? where id = ?", email, order.value());
        return order;
    }

    private void register() {
        assertThat(browser.post("/api/account/register",
            json(Map.of("email", "Ana@Example.com", "password", PASSWORD, "name", "Ana Pérez"))).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> signIn() {
        return browser.post("/api/account/login", json(Map.of("email", "ana@example.com", "password", PASSWORD)));
    }

    /** Sends every email due, as the job does, and answers the token of the last verification link delivered. */
    private String verificationToken() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
        Matcher link = LINK.matcher(resend.delivered().getLast().json().get("text").asText());
        assertThat(link.find()).as("the email carries the verification link").isTrue();
        return link.group(1);
    }

    private List<String> orderIds(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> ids = new ArrayList<>();
        for (JsonNode order : read(response).get("orders")) {
            ids.add(order.get("id").asText());
        }
        return ids;
    }

    private String json(Map<String, String> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
