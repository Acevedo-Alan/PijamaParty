package com.pijamaparty.store.payments.adapter.out.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentVerifier;
import com.pijamaparty.store.payments.application.PaymentVerifierContractTest;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MercadoPagoPaymentVerifier} against a {@link MercadoPagoStub}: the contract of every verifier, plus how it
 * reads MercadoPago's payment (UC-15, T-E3-07) and survives failures (RNF-21).
 */
class MercadoPagoPaymentVerifierContractTest extends PaymentVerifierContractTest<MercadoPagoPaymentVerifier> {

    private static final String TOKEN = "TEST-1234567890-access-token";

    /** One MercadoPago status per store status. */
    private static final Map<VerifiedStatus, String> STATUS = Map.of(
        VerifiedStatus.APPROVED, "approved",
        VerifiedStatus.REJECTED, "rejected",
        VerifiedStatus.PENDING, "in_process",
        VerifiedStatus.OTHER, "refunded");

    private final MercadoPagoStub stub = new MercadoPagoStub();

    @Override
    protected MercadoPagoPaymentVerifier createSubject() {
        return verifier(TOKEN, Duration.ofSeconds(2));
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void givenPayment(String id, long orderNumber, VerifiedStatus status, Money amount) {
        stub.holdsPayment(id, orderNumber, STATUS.get(status), BigDecimal.valueOf(amount.cents(), 2));
    }

    @Override
    protected void providerIsDown() {
        stub.answer(500, "{\"message\":\"internal_error\"}");
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAPayment_whenVerified_thenItIsAskedWithTheAccessToken() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));

        subject.verify(PAYMENT_ID);

        assertThat(stub.lastRequest().method()).isEqualTo("GET");
        assertThat(stub.lastRequest().path()).isEqualTo("/v1/payments/" + PAYMENT_ID);
        assertThat(stub.lastRequest().authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void givenEveryMercadoPagoStatus_whenVerified_thenEachMapsToTheStoresStatus() {
        Map<String, VerifiedStatus> expected = Map.of("approved", VerifiedStatus.APPROVED,
            "rejected", VerifiedStatus.REJECTED, "cancelled", VerifiedStatus.REJECTED,
            "pending", VerifiedStatus.PENDING, "in_process", VerifiedStatus.PENDING,
            "authorized", VerifiedStatus.PENDING, "in_mediation", VerifiedStatus.PENDING,
            "refunded", VerifiedStatus.OTHER, "charged_back", VerifiedStatus.OTHER);
        long id = 100;
        for (var status : expected.entrySet()) {
            stub.holdsPayment(Long.toString(id), 1001, status.getKey(), new BigDecimal("21000.5"));

            assertThat(subject.verify(Long.toString(id))).as(status.getKey())
                .isEqualTo(new PaymentVerifier.Verification.Found(new VerifiedPayment(Long.toString(id), 1001,
                    status.getValue(), Money.of(2_100_050))));
            id++;
        }
    }

    @Test
    void givenAnIdThatIsNotAMercadoPagoPaymentId_whenVerified_thenItIsNotFoundWithoutCallingTheProvider() {
        assertThat(subject.verify("../users/me")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenNoAccessToken_whenAPaymentIsVerified_thenItIsUnavailableWithoutCallingTheProvider() {
        MercadoPagoPaymentVerifier unconfigured = verifier("", Duration.ofSeconds(2));

        assertThat(unconfigured.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenTheProviderTakesLongerThanTheReadTimeout_whenVerified_thenItIsUnavailable() {
        givenPayment(PAYMENT_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));
        MercadoPagoPaymentVerifier impatient = verifier(TOKEN, Duration.ofMillis(200));
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenTheProviderIsUnreachable_whenVerified_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenTheProviderRefusesTheToken_whenVerified_thenItIsUnavailable() {
        stub.answer(401, "{\"message\":\"invalid access token\"}");

        assertThat(subject.verify(PAYMENT_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenUnusablePayments_whenVerified_thenTheyAreUnavailable() {
        String payment = """
            {"id": %s, "status": "%s", "external_reference": %s, "transaction_amount": %s, "currency_id": "%s"}
            """;
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "\"1001\"", "21000.50", "USD"), "currency");
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "\"1001\"", "21000.505", "ARS"), "decimals");
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "\"1001\"", "0", "ARS"), "amount");
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "null", "21000.50", "ARS"), "no reference");
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "\"ORDER-1001\"", "21000.50", "ARS"), "reference");
        assertUnavailable(payment.formatted(PAYMENT_ID, "approved", "\"0\"", "21000.50", "ARS"), "order zero");
        assertUnavailable(payment.formatted(PAYMENT_ID, "chargeback", "\"1001\"", "21000.50", "ARS"), "status");
        assertUnavailable(payment.formatted("42", "approved", "\"1001\"", "21000.50", "ARS"), "another id");
        assertUnavailable("<html>maintenance</html>", "not json");
    }

    private void assertUnavailable(String json, String why) {
        stub.holdsPaymentJson(PAYMENT_ID, json);

        assertThat(subject.verify(PAYMENT_ID)).as(why).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    private MercadoPagoPaymentVerifier verifier(String token, Duration readTimeout) {
        var properties = new MercadoPagoProperties(stub.baseUrl(), Duration.ofSeconds(1), readTimeout);
        return new MercadoPagoPaymentVerifier(properties, token);
    }
}
