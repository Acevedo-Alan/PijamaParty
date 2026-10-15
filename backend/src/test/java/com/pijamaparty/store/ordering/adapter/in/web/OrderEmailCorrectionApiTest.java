package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-17 (T-E3-23) end to end: the buyer corrects its email through the tracking link, with the real security filter
 * chain, Row Level Security and PostgreSQL, and the email job's use case sends the confirmation again to the Resend
 * stub. The
 * order is stored as the owner, bound to a tracking token the test holds.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class OrderEmailCorrectionApiTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MISSPELLED = "ana.perez@gmial.com";
    private static final String CORRECTED = "ana.perez@gmail.com";

    private final MockMvc mvc;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final JdbcTemplate owner;
    private final OrderId order = OrderId.of(UUID.randomUUID());
    private final String token = newToken();

    OrderEmailCorrectionApiTest(MockMvc mvc, ResendStub resend, SendNextEmail sendNextEmail,
                                PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.resend = resend;
        this.sendNextEmail = sendNextEmail;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void setUp() {
        OrderRows.insert(owner, order, OrderStatus.AWAITING_PAYMENT, Instant.now().plusSeconds(600));
        owner.update("update orders set tracking_token_hash = ?, customer_email = ? where id = ?",
            new TrackingToken(token).hash(), MISSPELLED, order.value());
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (gen_random_uuid(), ?, 0, gen_random_uuid(), 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 2000000, 1)
            """, order.value());
    }

    @AfterEach
    void cleanUp() {
        resend.reset();
        owner.execute("truncate email_outbox, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAPaidOrderWhoseConfirmationWasSent_whenTheBuyerCorrectsItsEmail_thenTheConfirmationGoesToTheNewOne(
        CapturedOutput logs) throws Exception {
        paid();
        buyersConfirmation("SENT", 1);

        correct(token, " Ana.Perez@Gmail.com ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationResent").value(true))
            .andExpect(content().string(not(containsString("ana.perez"))));

        assertThat(emailOfOrder()).isEqualTo(CORRECTED);
        assertThat(buyersConfirmations()).extracting(email -> email.get("version"), email -> email.get("status"))
            .containsExactly(Tuple.tuple(0, "SENT"), Tuple.tuple(1, "PENDING"));

        sendDueEmails();

        assertThat(resend.delivered()).singleElement().satisfies(email -> {
            assertThat(email.to()).isEqualTo(CORRECTED);
            assertThat(email.json().get("text").asText()).contains("San Martín 1234");
        });
        assertThat(logs.getAll()).doesNotContain(CORRECTED, MISSPELLED);
    }

    @Test
    void givenAPaidOrderWhoseConfirmationIsStillDue_whenTheBuyerCorrectsItsEmail_thenThatOneGoesToTheNewEmail()
        throws Exception {
        paid();
        buyersConfirmation("PENDING", 0);

        correct(token, CORRECTED)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationResent").value(true));
        sendDueEmails();

        assertThat(buyersConfirmations()).singleElement()
            .satisfies(email -> assertThat(email.get("status")).isEqualTo("SENT"));
        assertThat(resend.delivered()).singleElement()
            .satisfies(email -> assertThat(email.to()).isEqualTo(CORRECTED));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenTheBuyerCorrectsItsEmail_thenOnlyTheEmailChanges() throws Exception {
        correct(token, CORRECTED)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationResent").value(false));

        assertThat(emailOfOrder()).isEqualTo(CORRECTED);
        assertThat(buyersConfirmations()).isEmpty();
    }

    @Test
    void givenTheLinkOfTheConfirmationEmail_whenTheBuyerCorrectsItsEmailThroughIt_thenItIsCorrected()
        throws Exception {
        paid();
        buyersConfirmation("SENT", 1);
        String emailed = newToken();
        owner.update("insert into order_tracking_token (token_hash, order_id, created_at) values (?, ?, now())",
            new TrackingToken(emailed).hash(), order.value());

        correct(emailed, CORRECTED)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationResent").value(true));

        assertThat(emailOfOrder()).isEqualTo(CORRECTED);
        assertThat(buyersConfirmations()).hasSize(2);
    }

    @Test
    void givenAnExpiredOrder_whenTheBuyerCorrectsItsEmail_thenItIsRefusedAsClosed() throws Exception {
        owner.update("update orders set status = 'EXPIRED' where id = ?", order.value());

        correct(token, CORRECTED)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("ORDER_CLOSED"))
            .andExpect(jsonPath("$.status").value("EXPIRED"));

        assertThat(emailOfOrder()).isEqualTo(MISSPELLED);
    }

    @Test
    void givenAnInvalidEmail_whenTheBuyerCorrectsIt_thenItIsRefusedAndNothingChanges() throws Exception {
        paid();
        buyersConfirmation("SENT", 1);

        correct(token, "ana.perez@")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_EMAIL"));

        assertThat(emailOfOrder()).isEqualTo(MISSPELLED);
        assertThat(buyersConfirmations()).hasSize(1);
    }

    @Test
    void givenABodyThatIsNotAnEmailField_whenItArrives_thenItIsABadRequest() throws Exception {
        for (String body : new String[] {"{}", "{\"email\":null}", "not json",
            "{\"email\":\"" + "a".repeat(291) + "@gmail.com\"}"}) {
            mvc.perform(patch("/api/orders/{token}/email", token).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }

        assertThat(emailOfOrder()).isEqualTo(MISSPELLED);
    }

    @Test
    void givenATokenOfNoOrderOrAMalformedOne_whenTheEmailIsCorrected_thenItIsNotFound() throws Exception {
        for (String unknown : new String[] {newToken(), "short", "a".repeat(42) + "!"}) {
            correct(unknown, CORRECTED)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.problem").value("ORDER_NOT_FOUND"));
        }

        assertThat(emailOfOrder()).isEqualTo(MISSPELLED);
    }

    @Test
    void givenACorrectionWithoutTheCsrfToken_whenItArrives_thenItIsForbidden() throws Exception {
        mvc.perform(patch("/api/orders/{token}/email", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + CORRECTED + "\"}"))
            .andExpect(status().isForbidden());

        assertThat(emailOfOrder()).isEqualTo(MISSPELLED);
    }

    private ResultActions correct(String trackingToken, String email) throws Exception {
        return mvc.perform(patch("/api/orders/{token}/email", trackingToken).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    /** As the email job runs: as {@code SYSTEM}, until none is due. */
    private void sendDueEmails() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
    }

    private void paid() {
        owner.update("update orders set status = 'PAID' where id = ?", order.value());
    }

    /** The buyer's confirmation as the payment enqueued it (version 0), after {@code attempts}. */
    private void buyersConfirmation(String status, int attempts) {
        boolean sent = status.equals("SENT");
        owner.update("""
            insert into email_outbox (id, type, reference, version, status, attempts, next_attempt_at,
                                      provider_message_id, created_at, sent_at, updated_at)
            values (gen_random_uuid(), 'ORDER_PAID_CUSTOMER', ?, 0, ?, ?, now(), ?, now(), ?, now())
            """, order.value(), status, attempts, sent ? "re_first" : null,
            sent ? Timestamp.from(Instant.now()) : null);
    }

    private List<Map<String, Object>> buyersConfirmations() {
        return owner.queryForList("select version, status from email_outbox where type = 'ORDER_PAID_CUSTOMER' "
            + "and reference = ? order by version", order.value());
    }

    private String emailOfOrder() {
        return owner.queryForObject("select customer_email from orders where id = ?", String.class, order.value());
    }

    /** A token as the server issues them: 32 random bytes in unpadded base64url. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
