package com.pijamaparty.store.notifications.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-17, UC-18 (T-E3-22): Resend's bounce and complaint notifications through the API, with the real security filter
 * chain, Row Level Security and PostgreSQL. The sent emails are stored as the owner, as the outbox job leaves them.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class ResendWebhookApiTest {

    private static final String WEBHOOK = "/api/mail/webhook";
    private static final String BUYER_MESSAGE = "4ef9a417-02e9-4d39-ad75-9611e0fcc33c";
    private static final String ADMIN_MESSAGE = "7c2b1e8a-5d43-4f6e-9a01-2b3c4d5e6f70";
    private static final String BUYER_EMAIL = "ana@example.com";
    private static final String SUBJECT = "Recibimos tu pago - Pedido #1001";

    private final MockMvc mvc;
    private final JdbcTemplate owner;
    private final UUID order = UUID.randomUUID();

    ResendWebhookApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_event, email_outbox");
    }

    @Test
    void givenTheBuyersEmailBouncedForGood_whenNotified_thenItIsBouncedAndTheEventRecordedWithoutPersonalData(
        CapturedOutput logs) throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", bounced(BUYER_MESSAGE, "Permanent")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("BOUNCED");
        assertThat(owner.queryForList("select * from email_event")).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("provider", "RESEND").containsEntry("event_id", "msg_1")
            .containsEntry("type", "PERMANENT_BOUNCE").containsEntry("provider_message_id", BUYER_MESSAGE));
        assertThat(logLines(logs)).contains("The ORDER_PAID_CUSTOMER email of order " + order + " bounced")
            .doesNotContain(BUYER_EMAIL, SUBJECT);
    }

    @Test
    void givenARepeatedBounceNotification_whenItArrivesASecondTime_thenItIsNotProcessedAgain(CapturedOutput logs)
        throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);
        String body = bounced(BUYER_MESSAGE, "Permanent");
        notifyEvent("msg_1", body).andExpect(status().isOk());

        notifyEvent("msg_1", body).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("BOUNCED");
        assertThat(owner.queryForObject("select count(*) from email_event", Long.class)).isOne();
        assertThat(logLines(logs).lines().filter(line -> line.contains("bounced"))).hasSize(1);
    }

    @Test
    void givenTheBuyersAddressSuppressed_whenNotified_thenTheEmailIsBounced() throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", event("email.suppressed", BUYER_MESSAGE, "")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("BOUNCED");
        assertThat(eventTypes()).containsExactly("SUPPRESSED");
    }

    @Test
    void givenABounceWithoutABounceType_whenNotified_thenItIsAPermanentBounce() throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", event("email.bounced", BUYER_MESSAGE, "")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("BOUNCED");
        assertThat(eventTypes()).containsExactly("PERMANENT_BOUNCE");
    }

    @Test
    void givenATransientBounceOrAComplaint_whenNotified_thenOnlyTheEventIsRecordedAndTheEmailStaysSent(
        CapturedOutput logs) throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", bounced(BUYER_MESSAGE, "Transient")).andExpect(status().isOk());
        notifyEvent("msg_2", event("email.complained", BUYER_MESSAGE, "")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(eventTypes()).containsExactlyInAnyOrder("TRANSIENT_BOUNCE", "COMPLAINT");
        assertThat(logLines(logs)).contains("Resend reported COMPLAINT for email " + BUYER_MESSAGE)
            .doesNotContain(BUYER_EMAIL);
    }

    @Test
    void givenTheAdministratorsNoticeBounced_whenNotified_thenItIsBouncedAndLoggedAsAnError(CapturedOutput logs)
        throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);
        sent("ORDER_PAID_ADMIN", ADMIN_MESSAGE);

        notifyEvent("msg_1", bounced(ADMIN_MESSAGE, "Permanent")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_ADMIN")).isEqualTo("BOUNCED");
        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(logLines(logs).lines().filter(line -> line.contains("ERROR")))
            .anyMatch(line -> line.contains("The administrator's notice of order " + order + " bounced"));
    }

    @Test
    void givenABounceOfAnEmailThisStoreDidNotSend_whenNotified_thenItIsRecordedAndIgnored(CapturedOutput logs)
        throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", bounced("another-app-email", "Permanent")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(eventTypes()).containsExactly("PERMANENT_BOUNCE");
        assertThat(logLines(logs)).contains("email another-app-email, which is no sent email of this store");
    }

    @Test
    void givenAMissingInvalidOrOutOfDateSignature_whenNotified_thenItIsRefusedAndNothingIsRecorded(
        CapturedOutput logs) throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);
        byte[] body = bytes(bounced(BUYER_MESSAGE, "Permanent"));
        String now = Long.toString(Instant.now().getEpochSecond());
        String old = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());

        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(signed("msg_1", now, ResendWebhooks.sign("msg_1", now, bytes("{}")), body))
            .andExpect(status().isUnauthorized());
        mvc.perform(signed("msg_1", old, ResendWebhooks.sign("msg_1", old, body), body))
            .andExpect(status().isUnauthorized());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(eventTypes()).isEmpty();
        assertThat(logLines(logs)).contains("Resend notification refused").doesNotContain(BUYER_EMAIL);
    }

    @Test
    void givenSignedNotificationsThisStoreDoesNotUse_whenNotified_thenTheyAreAcceptedAndNothingIsRecorded()
        throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);

        notifyEvent("msg_1", event("email.delivered", BUYER_MESSAGE, "")).andExpect(status().isOk());
        notifyEvent("msg_2", "not json").andExpect(status().isOk());
        notifyEvent("msg_3", "[1, 2]").andExpect(status().isOk());
        notifyEvent("msg_4", "{\"type\":\"email.bounced\",\"data\":{}}").andExpect(status().isOk());
        notifyEvent("msg_5", event("email.bounced", "not an id!", "")).andExpect(status().isOk());

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(eventTypes()).isEmpty();
    }

    @Test
    void givenABodyOver64KiB_whenNotified_thenItIsTooLargeAndNothingIsRecorded() throws Exception {
        sent("ORDER_PAID_CUSTOMER", BUYER_MESSAGE);
        String padding = "x".repeat(ResendWebhookController.MAX_BODY_BYTES);
        byte[] body = bytes(event("email.bounced", BUYER_MESSAGE, ",\"padding\":\"" + padding + "\""));
        String now = Long.toString(Instant.now().getEpochSecond());

        mvc.perform(signed("msg_1", now, ResendWebhooks.sign("msg_1", now, body), body))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(emailStatus("ORDER_PAID_CUSTOMER")).isEqualTo("SENT");
        assertThat(eventTypes()).isEmpty();
    }

    /** An email of the order, sent by the job with the provider's id. */
    private void sent(String type, String providerMessageId) {
        owner.update("""
            insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, provider_message_id,
                                      created_at, sent_at, updated_at)
            values (gen_random_uuid(), ?, ?, 'SENT', 1, now(), ?, now(), now(), now())
            """, type, order, providerMessageId);
    }

    private String emailStatus(String type) {
        return owner.queryForObject("select status from email_outbox where type = ? and reference = ?", String.class,
            type, order);
    }

    private List<String> eventTypes() {
        return owner.queryForList("select type from email_event", String.class);
    }

    /** As Resend documents {@code email.bounced}: the recipient and the subject come along, and must not leak. */
    private static String bounced(String messageId, String bounceType) {
        return event("email.bounced", messageId,
            ",\"bounce\":{\"message\":\"The recipient's mail server rejected it\",\"subType\":\"General\","
                + "\"type\":\"" + bounceType + "\"}");
    }

    private static String event(String type, String messageId, String extraData) {
        return "{\"type\":\"" + type + "\",\"created_at\":\"2026-10-14T15:00:00.000Z\",\"data\":{"
            + "\"email_id\":\"" + messageId + "\",\"from\":\"Pijama Party <pedidos@notificaciones.example>\","
            + "\"to\":[\"" + BUYER_EMAIL + "\"],\"subject\":\"" + SUBJECT + "\"" + extraData + "}}";
    }

    private ResultActions notifyEvent(String id, String body) throws Exception {
        byte[] bytes = bytes(body);
        String now = Long.toString(Instant.now().getEpochSecond());
        return mvc.perform(signed(id, now, ResendWebhooks.sign(id, now, bytes), bytes));
    }

    private static MockHttpServletRequestBuilder signed(String id, String timestamp, String signature, byte[] body) {
        return post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
            .header("svix-id", id).header("svix-timestamp", timestamp).header("svix-signature", signature);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String logLines(CapturedOutput output) {
        return output.getAll().lines()
            .filter(line -> line.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*"))
            .collect(Collectors.joining("\n"));
    }

}
