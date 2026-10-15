package com.pijamaparty.store.payments.adapter.out.modo;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentVerifier;
import com.pijamaparty.store.payments.application.PaymentVerifierContractTest;
import com.pijamaparty.store.payments.domain.PaymentReference;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * {@link ModoPaymentVerifier} against a {@link ModoStub}: the contract of every verifier, plus how it reads MODO's
 * payment request data (UC-15, T-E3b-03), shares the gateway's token and survives failures (RNF-21).
 */
@ExtendWith(OutputCaptureExtension.class)
class ModoPaymentVerifierContractTest extends PaymentVerifierContractTest<ModoPaymentVerifier> {

    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final String DATA = "/v2/payment-requests/" + PAYMENT_ID + "/data";

    /** One MODO status per store status MODO can report: it never shows a payment still in progress. */
    private static final Map<VerifiedStatus, String> STATUS = Map.of(
        VerifiedStatus.APPROVED, "ACCEPTED",
        VerifiedStatus.REJECTED, "REJECTED",
        VerifiedStatus.OTHER, "REFUNDED");

    private final ModoStub stub = new ModoStub();

    @Override
    protected ModoPaymentVerifier createSubject() {
        return verifier(Duration.ofSeconds(2), ModoStub.USERNAME, ModoStub.PASSWORD, true);
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MODO;
    }

    /** MODO writes the amount with three decimals. */
    @Override
    protected void givenPayment(String id, long orderNumber, VerifiedStatus status, Money amount) {
        stub.holdsPayment(id, STATUS.get(status), BigDecimal.valueOf(amount.cents(), 2).setScale(3).toPlainString());
    }

    @Override
    protected void providerIsDown() {
        stub.answerData(500, "{\"message\":\"internal_error\"}");
    }

    /** MODO does not return the order: the payment refers to the payment request it paid. */
    @Override
    protected PaymentReference expectedReference(String id, long orderNumber) {
        return new PaymentReference.ByCheckout(id);
    }

    @Override
    protected List<VerifiedStatus> reportableStatuses() {
        return List.copyOf(STATUS.keySet());
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenTheVerifier_whenAskedHowItFindsPayments_thenItIsByTheirCheckout() {
        assertThat(subject.verifiesByCheckout()).isTrue();
    }

    @Test
    void givenAPayment_whenVerified_thenItsDataIsAskedWithATokenAndTheMerchantsName() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));

        subject.verify(PAYMENT_ID);

        ModoStub.Request asked = stub.requestsTo(DATA).getLast();
        assertThat(asked.method()).isEqualTo("GET");
        assertThat(asked.authorization()).startsWith("Bearer token-");
        assertThat(asked.userAgent()).isEqualTo("PijamaParty");
    }

    @Test
    void givenEveryFinalModoStatus_whenVerified_thenEachMapsToTheStoresStatus() {
        Map<String, VerifiedStatus> expected = Map.of("ACCEPTED", VerifiedStatus.APPROVED,
            "REJECTED", VerifiedStatus.REJECTED, "REFUNDED", VerifiedStatus.OTHER,
            "PARTIAL_REFUND", VerifiedStatus.OTHER);
        for (var status : expected.entrySet()) {
            String id = "pr-" + status.getKey().toLowerCase(Locale.ROOT).replace('_', '-');
            stub.holdsPayment(id, status.getKey(), "21000.500");

            assertThat(subject.verify(id)).as(status.getKey())
                .isEqualTo(new PaymentVerifier.Verification.Found(new VerifiedPayment(id,
                    new PaymentReference.ByCheckout(id), status.getValue(), Money.of(2_100_050))));
        }
    }

    @Test
    void givenAnIdThatIsNotAPaymentRequestId_whenVerified_thenItIsNotFoundWithoutCallingMODO() {
        assertThat(subject.verify("../stores/companies")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenNoCredentials_whenAPaymentIsVerified_thenItIsUnavailableWithoutCallingMODO() {
        ModoPaymentVerifier unconfigured = verifier(Duration.ofSeconds(2), "", "", false);

        assertThat(unconfigured.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenMODORefusesTheTokenItGave_whenVerified_thenTheTokenIsRenewedOnceAndThePaymentFound() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));
        subject.verify(PAYMENT_ID);
        stub.revokeTokens();

        assertThat(subject.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Found.class);
        assertThat(stub.requestsTo(ModoAccessTokens.TOKEN_PATH)).hasSize(2);
    }

    @Test
    void givenMODOTakesLongerThanTheReadTimeout_whenVerified_thenItIsUnavailable() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));
        ModoPaymentVerifier impatient = verifier(Duration.ofMillis(200), ModoStub.USERNAME, ModoStub.PASSWORD, true);
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenMODOIsUnreachable_whenVerified_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenUnusableAnswers_whenVerified_thenTheyAreUnavailableAndThePayerIsNotLogged(CapturedOutput logs) {
        String data = """
            {"id": "%s", "status": "%s", "transaction_data": {"payment_request_id": "%s", "amount": %s,
             "dni": "30111222", "first_name": "Zoraida"}}
            """;
        assertUnavailable(data.formatted("pr-other", "ACCEPTED", "pr-other", "\"21000.500\""), "another id");
        assertUnavailable(data.formatted(PAYMENT_ID, "ACCEPTED", "pr-other", "\"21000.500\""), "another request");
        assertUnavailable(data.formatted(PAYMENT_ID, "CREATED", PAYMENT_ID, "\"21000.500\""), "status");
        assertUnavailable(data.formatted(PAYMENT_ID, "ACCEPTED", PAYMENT_ID, "\"21000.505\""), "decimals");
        assertUnavailable(data.formatted(PAYMENT_ID, "ACCEPTED", PAYMENT_ID, "\"0.000\""), "amount");
        assertUnavailable(data.formatted(PAYMENT_ID, "ACCEPTED", PAYMENT_ID, "null"), "no amount");
        assertUnavailable("{\"id\": \"" + PAYMENT_ID + "\", \"status\": \"ACCEPTED\"}", "no transaction");
        assertUnavailable("<html>maintenance</html>", "not json");

        assertThat(logs.getAll()).contains("payment request " + PAYMENT_ID).doesNotContain("30111222", "Zoraida");
    }

    @Test
    void givenAnAmountWrittenAsANumber_whenVerified_thenItIsReadToo() {
        stub.answerData(200, """
            {"id": "%1$s", "status": "ACCEPTED", "transaction_data": {"payment_request_id": "%1$s", "amount": 117}}
            """.formatted(PAYMENT_ID));

        assertThat(subject.verify(PAYMENT_ID)).isEqualTo(new PaymentVerifier.Verification.Found(new VerifiedPayment(
            PAYMENT_ID, new PaymentReference.ByCheckout(PAYMENT_ID), VerifiedStatus.APPROVED, Money.of(11_700))));
    }

    private void assertUnavailable(String json, String why) {
        stub.answerData(200, json);

        assertThat(subject.verify(PAYMENT_ID)).as(why).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    private ModoPaymentVerifier verifier(Duration readTimeout, String username, String password, boolean configured) {
        var properties = new ModoProperties(true, false, stub.baseUrl(), URI.create("https://site.preprod.modo.com.ar"),
            "P-123", "1CSI", "PijamaParty", Duration.ofSeconds(1), readTimeout);
        var client = ModoRestClients.create(properties);
        return new ModoPaymentVerifier(client, new ModoAccessTokens(client, username, password,
            Clock.fixed(NOW, ZoneOffset.UTC)), configured);
    }
}
