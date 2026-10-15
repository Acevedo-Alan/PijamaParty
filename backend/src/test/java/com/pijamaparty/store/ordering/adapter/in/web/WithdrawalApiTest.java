package com.pijamaparty.store.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.notifications.adapter.out.resend.ResendStub;
import com.pijamaparty.store.notifications.application.SendNextEmail;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * UC-29 (T-E3-25) end to end: a guest sends the withdrawal form, with the real security filter chain, Row Level
 * Security and PostgreSQL, and the email job's use case sends the code and the notice to the Resend stub. The order is
 * stored as the owner, for {@code ana@example.com}.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class WithdrawalApiTest {

    private static final String EMAIL = "ana@example.com";
    private static final String ADMIN = "admin@pijamaparty.test";
    private static final String CODE = "^ARR-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$";

    private final MockMvc mvc;
    private final ResendStub resend;
    private final SendNextEmail sendNextEmail;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final OrderId order = OrderId.of(UUID.randomUUID());
    private long number;

    WithdrawalApiTest(MockMvc mvc, ResendStub resend, SendNextEmail sendNextEmail, ObjectMapper json,
                      PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.resend = resend;
        this.sendNextEmail = sendNextEmail;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void setUp() {
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now().minusSeconds(3600));
        number = OrderRows.numberOf(owner, order);
    }

    @AfterEach
    void cleanUp() {
        resend.reset();
        owner.execute("truncate email_outbox, withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAPaidOrder_whenTheFormIsSentWithItsNumberAndEmail_thenACodeIsShownEmailedAndTheRequestRecorded(
        CapturedOutput logs) throws Exception {
        String code = codeOf(send("#" + number, " Ana@Example.com ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(matchesPattern(CODE))));

        assertThat(requests()).singleElement().satisfies(request -> {
            assertThat(request.get("code")).isEqualTo(code);
            assertThat(request.get("status")).isEqualTo("RECEIVED");
            assertThat(request.get("out_of_term")).isEqualTo(false);
        });

        sendDueEmails();

        assertThat(resend.delivered()).hasSize(2);
        assertThat(resend.delivered()).filteredOn(email -> email.to().equals(EMAIL)).singleElement()
            .satisfies(email -> {
                assertThat(email.json().get("subject").asText()).contains("#" + number);
                assertThat(email.json().get("text").asText()).contains(code, "Ana");
            });
        assertThat(resend.delivered()).filteredOn(email -> email.to().equals(ADMIN)).singleElement()
            .satisfies(email -> assertThat(email.json().get("text").asText())
                .contains(code, "#" + number).doesNotContain("fuera del plazo"));
        assertThat(logs.getAll()).doesNotContain(EMAIL);
    }

    @Test
    void givenAnOrderNumberWithAnEmailThatDoesNotMatch_whenTheFormIsSent_thenAGenericAnswerAndNothingIsRecorded()
        throws Exception {
        String wrongEmail = send(Long.toString(number), "otra@example.com")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("NO_MATCHING_ORDER"))
            .andReturn().getResponse().getContentAsString();
        String unknownNumber = send(Long.toString(number + 1000), EMAIL)
            .andExpect(status().isUnprocessableEntity())
            .andReturn().getResponse().getContentAsString();

        assertThat((Object) ((ObjectNode) json.readTree(unknownNumber)).without(List.of("instance")))
            .isEqualTo(((ObjectNode) json.readTree(wrongEmail)).without(List.of("instance")));
        assertThat(requests()).isEmpty();
        assertThat(outbox()).isEmpty();
    }

    @Test
    void givenAnOrderWithAnActiveRequest_whenAnotherIsSent_thenTheSameCodeIsShownAndNoSecondRequestIsCreated()
        throws Exception {
        String first = codeOf(send(Long.toString(number), EMAIL).andExpect(status().isCreated()));

        send(Long.toString(number), EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(first));

        assertThat(requests()).hasSize(1);
        assertThat(outbox()).hasSize(2);
    }

    @Test
    void givenAnOrderDeliveredMoreThan10DaysAgo_whenTheFormIsSent_thenTheRequestIsRecordedFlaggedAsOutOfTerm()
        throws Exception {
        owner.update("""
            update orders set status = 'DELIVERED', shipment_tracking_number = 'CA123456789AR',
                              created_at = now() - interval '20 days', delivered_at = now() - interval '12 days'
            where id = ?
            """, order.value());

        send(Long.toString(number), EMAIL).andExpect(status().isCreated());

        assertThat(requests()).singleElement()
            .satisfies(request -> assertThat(request.get("out_of_term")).isEqualTo(true));
        sendDueEmails();
        assertThat(resend.delivered()).filteredOn(email -> email.to().equals(ADMIN)).singleElement()
            .satisfies(email -> assertThat(email.json().get("text").asText()).contains("fuera del plazo"));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenTheFormIsSent_thenItIsRefusedAndNothingIsRecorded() throws Exception {
        owner.update("update orders set status = 'AWAITING_PAYMENT' where id = ?", order.value());

        send(Long.toString(number), EMAIL)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("ORDER_NOT_WITHDRAWABLE"))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));

        assertThat(requests()).isEmpty();
    }

    @Test
    void givenABodyThatIsNotTheForm_whenItArrives_thenItIsABadRequest() throws Exception {
        for (String body : new String[] {"{}", "{\"orderNumber\":\"1001\"}", "{\"email\":\"" + EMAIL + "\"}",
            "not json", "{\"orderNumber\":\"" + "1".repeat(21) + "\",\"email\":\"" + EMAIL + "\"}"}) {
            mvc.perform(post("/api/withdrawals").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }

        assertThat(requests()).isEmpty();
    }

    @Test
    void givenAFormWithoutTheCsrfToken_whenItArrives_thenItIsForbidden() throws Exception {
        mvc.perform(post("/api/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + number + "\",\"email\":\"" + EMAIL + "\"}"))
            .andExpect(status().isForbidden());

        assertThat(requests()).isEmpty();
    }

    private ResultActions send(String orderNumber, String email) throws Exception {
        return mvc.perform(post("/api/withdrawals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("orderNumber", orderNumber, "email", email))));
    }

    private String codeOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("code").asText();
    }

    /** As the email job runs: as {@code SYSTEM}, until none is due. */
    private void sendDueEmails() {
        while (!(CurrentActor.callAsSystem(sendNextEmail::execute) instanceof SendNextEmail.Result.NoneDue)) {
            // next one
        }
    }

    private List<Map<String, Object>> requests() {
        return owner.queryForList("select code, status, out_of_term from withdrawal_request where order_id = ?",
            order.value());
    }

    private List<Map<String, Object>> outbox() {
        return owner.queryForList("select type from email_outbox");
    }
}
