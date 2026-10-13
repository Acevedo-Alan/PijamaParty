package com.pijamaparty.store.notifications.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * RNF-23, UC-17 end to end: the scheduled job sends the outbox as {@code SYSTEM}, composing each email from the real
 * order and sending it to the Resend stub, over the real tables and policies.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class EmailOutboxDispatcherTest {

    private static final Pattern TRACKING_LINK = Pattern.compile("http://localhost:5173/pedido/([A-Za-z0-9_-]{43})");

    private final EmailOutboxDispatcher job;
    private final ResendStub resend;
    private final MockMvc mvc;
    private final JdbcTemplate owner;
    private final OrderId order = OrderId.of(UUID.randomUUID());
    private long number;

    EmailOutboxDispatcherTest(EmailOutboxDispatcher job, ResendStub resend, MockMvc mvc,
                              PostgreSQLContainer<?> postgres) {
        this.job = job;
        this.resend = resend;
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    /** A paid order with its two emails due, as the payment leaves them. */
    @BeforeEach
    void setUp() {
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now().minusSeconds(60));
        number = OrderRows.numberOf(owner, order);
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (gen_random_uuid(), ?, 0, gen_random_uuid(), 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 1000000, 2)
            """, order.value());
        for (String type : List.of("ORDER_PAID_CUSTOMER", "ORDER_PAID_ADMIN")) {
            owner.update("""
                insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, created_at,
                                          updated_at)
                values (gen_random_uuid(), ?, ?, 'PENDING', 0, now(), now(), now())
                """, type, order.value());
        }
    }

    @AfterEach
    void cleanUp() {
        resend.reset();
        owner.execute("truncate email_outbox, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAPaidOrdersEmailsDue_whenTheJobRuns_thenTheBuyerAndTheAdministratorEachGetOneEmail(
        CapturedOutput logs) {
        job.run();
        job.run();

        assertThat(resend.delivered()).extracting(ResendStub.Request::to)
            .containsExactlyInAnyOrder("ana@example.com", "admin@pijamaparty.test");
        for (Map<String, Object> email : outbox()) {
            assertThat(email.get("status")).isEqualTo("SENT");
            assertThat(email.get("attempts")).isEqualTo(1);
            assertThat((String) email.get("provider_message_id")).isNotBlank();
            assertThat(email.get("sent_at")).isNotNull();
        }
        assertThat(logLines(logs)).contains("Email outbox sent 2, will retry 0, failed 0")
            .doesNotContain("ana@example.com", "admin@pijamaparty.test", "San Martín");
    }

    @Test
    void givenThePaymentConfirmedEmail_whenSent_thenItsTrackingLinkOpensTheOrderAndOnlyItsHashIsKept()
        throws Exception {
        job.run();

        ResendStub.Request email = resend.delivered().stream()
            .filter(request -> request.to().equals("ana@example.com")).findFirst().orElseThrow();
        assertThat(email.json().get("subject").asText()).isEqualTo("Recibimos tu pago - Pedido #" + number);
        assertThat(email.json().get("from").asText()).isEqualTo("Pijama Party <pedidos@pijamaparty.test>");
        assertThat(email.json().get("reply_to").asText()).isEqualTo("hola@pijamaparty.test");
        String text = email.json().get("text").asText();
        assertThat(text).contains("Pijama Luna - Talle M - Rosa x 2", "Total: $ 23.000,00", "San Martín 1234",
            "Santa Fe, Santa Fe");
        Matcher link = TRACKING_LINK.matcher(text);
        assertThat(link.find()).isTrue();
        String token = link.group(1);

        mvc.perform(get("/api/orders/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(number))
            .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(owner.queryForList("select token_hash from order_tracking_token where order_id = ?",
            String.class, order.value())).hasSize(1).doesNotContain(token);
    }

    @Test
    void givenResendDown_whenTheJobRuns_thenTheOrderStaysPaidAndTheEmailsAreRetriedAMinuteLater(
        CapturedOutput logs) {
        resend.answer(503, "{\"statusCode\":503}");
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        job.run();

        assertThat(OrderRows.statusOf(owner, order)).contains(OrderStatus.PAID);
        for (Map<String, Object> email : outbox()) {
            assertThat(email.get("status")).isEqualTo("PENDING");
            assertThat(email.get("attempts")).isEqualTo(1);
            assertThat(((Timestamp) email.get("next_attempt_at")).toInstant())
                .isAfterOrEqualTo(before.plusSeconds(60)).isBefore(before.plusSeconds(120));
        }
        assertThat(logLines(logs)).contains("Email outbox sent 0, will retry 2, failed 0");
    }

    @Test
    void givenResendBackAtTheRetry_whenTheJobRunsThen_thenEachEmailIsSentOnce() {
        resend.answer(503, "{\"statusCode\":503}");
        job.run();
        resend.reset();
        owner.update("update email_outbox set next_attempt_at = now()");

        job.run();
        job.run();

        assertThat(resend.delivered()).hasSize(2);
        assertThat(outbox()).allSatisfy(email -> {
            assertThat(email.get("status")).isEqualTo("SENT");
            assertThat(email.get("attempts")).isEqualTo(2);
        });
    }

    @Test
    void givenResendRejectsTheEmails_whenTheJobRuns_thenTheyFailAndAreLoggedWithoutPersonalData(
        CapturedOutput logs) {
        resend.answer(422, "{\"statusCode\":422,\"name\":\"validation_error\"}");

        job.run();
        job.run();

        assertThat(resend.requests()).hasSize(2);
        assertThat(outbox()).allSatisfy(email -> assertThat(email.get("status")).isEqualTo("FAILED"));
        assertThat(logLines(logs))
            .contains("Email ORDER_PAID_CUSTOMER about " + order.value() + " failed and will not be sent")
            .contains("Email ORDER_PAID_ADMIN about " + order.value() + " failed and will not be sent")
            .doesNotContain("ana@example.com", "admin@pijamaparty.test");
    }

    @Test
    void givenEmailsNotDueYet_whenTheJobRuns_thenNothingIsSent() {
        owner.update("update email_outbox set next_attempt_at = now() + interval '5 minutes'");

        job.run();

        assertThat(resend.requests()).isEmpty();
        assertThat(outbox()).allSatisfy(email -> assertThat(email.get("attempts")).isEqualTo(0));
    }

    private List<Map<String, Object>> outbox() {
        return owner.queryForList("select * from email_outbox where reference = ?", order.value());
    }

    /** What the application logged, without MockMvc's dumps of the requests. */
    private static String logLines(CapturedOutput output) {
        return output.getAll().lines()
            .filter(line -> line.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*"))
            .collect(Collectors.joining("\n"));
    }
}
