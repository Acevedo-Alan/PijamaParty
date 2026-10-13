package com.pijamaparty.store.notifications.adapter.out.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.notifications.application.EmailSender;
import com.pijamaparty.store.notifications.application.EmailSenderContractTest;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * {@link ResendEmailSender} against a {@link ResendStub}: the contract of every sender, plus the request it sends
 * (T-E3-09) and how it reads Resend's answers and failures (RNF-21, RNF-23).
 */
@ExtendWith(OutputCaptureExtension.class)
class ResendEmailSenderContractTest extends EmailSenderContractTest<ResendEmailSender> {

    private static final String API_KEY = "re_123456789_test";
    private static final String FROM = "Pijama Party <pedidos@notificaciones.pijamaparty.example>";
    private static final String REPLY_TO = "hola@pijamaparty.example";

    private final ResendStub stub = new ResendStub();

    @Override
    protected ResendEmailSender createSubject() {
        return sender(API_KEY, FROM, REPLY_TO, Duration.ofSeconds(2));
    }

    @Override
    protected List<String> deliveredTo() {
        return stub.delivered().stream().map(ResendStub.Request::to).toList();
    }

    @Override
    protected void serviceIsDown() {
        stub.answer(500, "{\"statusCode\":500,\"name\":\"internal_server_error\"}");
    }

    @Override
    protected void serviceRejectsTheEmail() {
        stub.answer(422, "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"Invalid `to` field\"}");
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAnEmail_whenSent_thenResendGetsTheSenderTheRecipientTheReplyToAndBothBodies() {
        subject.send(MESSAGE);

        ResendStub.Request request = stub.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/emails");
        assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
        JsonNode body = request.json();
        assertThat(body.get("from").asText()).isEqualTo(FROM);
        assertThat(body.get("to")).hasSize(1);
        assertThat(body.get("to").get(0).asText()).isEqualTo("ana@example.com");
        assertThat(body.get("reply_to").asText()).isEqualTo(REPLY_TO);
        assertThat(body.get("subject").asText()).isEqualTo(MESSAGE.subject());
        assertThat(body.get("html").asText()).isEqualTo(MESSAGE.html());
        assertThat(body.get("text").asText()).isEqualTo(MESSAGE.text());
    }

    @Test
    void givenNoReplyTo_whenSent_thenTheRequestHasNone() {
        sender(API_KEY, FROM, " ", Duration.ofSeconds(2)).send(MESSAGE);

        assertThat(stub.requests().getFirst().json().has("reply_to")).isFalse();
    }

    @Test
    void givenResendTakesIt_whenSent_thenTheAnswersIdIsTheProviderMessageId() {
        stub.answer(200, "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}");

        assertThat(subject.send(MESSAGE))
            .isEqualTo(new EmailSender.Outcome.Sent("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794"));
    }

    @Test
    void givenNoApiKeyOrNoSender_whenSent_thenItIsUnavailableWithoutCallingResend() {
        assertThat(sender("", FROM, REPLY_TO, Duration.ofSeconds(2)).send(MESSAGE))
            .isInstanceOf(EmailSender.Outcome.Unavailable.class);
        assertThat(sender(API_KEY, "", REPLY_TO, Duration.ofSeconds(2)).send(MESSAGE))
            .isInstanceOf(EmailSender.Outcome.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenABadRequest_whenSent_thenItIsRejected() {
        stub.answer(400, "{\"statusCode\":400,\"name\":\"invalid_parameter\"}");

        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Rejected.class);
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void givenAnAnswerThatMayPassLater_whenSent_thenItIsUnavailable(int status) {
        stub.answer(status, "{\"statusCode\":" + status + "}");

        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);
    }

    @Test
    void givenResendTakesLongerThanTheReadTimeout_whenSent_thenItIsUnavailable() {
        ResendEmailSender impatient = sender(API_KEY, FROM, REPLY_TO, Duration.ofMillis(200));
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);
    }

    @Test
    void givenResendUnreachable_whenSent_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerWithoutAnIdOrNotJson_whenSent_thenItIsUnavailable() {
        stub.answer(200, "{}");
        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);

        stub.answer(200, "<html>maintenance</html>");
        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);
    }

    @Test
    void givenAFailure_whenLogged_thenOnlyTheStatusIsLogged(CapturedOutput output) {
        serviceRejectsTheEmail();

        subject.send(MESSAGE);

        String logs = output.getAll().lines().collect(Collectors.joining("\n"));
        assertThat(logs).contains("HTTP 422").doesNotContain("ana@example.com", "Pedido #1001", "Invalid `to`");
    }

    private ResendEmailSender sender(String apiKey, String from, String replyTo, Duration readTimeout) {
        return new ResendEmailSender(new ResendProperties(stub.baseUrl(), Duration.ofSeconds(1), readTimeout),
            apiKey, from, replyTo);
    }
}
