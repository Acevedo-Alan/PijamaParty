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
 * {@link MercadoPagoPaymentVerifier} against a {@link MercadoPagoStub} of the Orders API: the contract of every
 * verifier, plus how it reads a MercadoPago order (UC-15, T-E3-07, T-E3-34) and survives failures (RNF-21).
 */
class MercadoPagoPaymentVerifierContractTest extends PaymentVerifierContractTest<MercadoPagoPaymentVerifier> {

    private static final String TOKEN = "TEST-1234567890-access-token";
    private static final String ORDER_ID = "ORDTST01M7M43W866NP578ACQYTA5KJ9";

    /** One order status per store status. */
    private static final Map<VerifiedStatus, String> STATUS = Map.of(
        VerifiedStatus.APPROVED, "processed",
        VerifiedStatus.REJECTED, "failed",
        VerifiedStatus.PENDING, "processing",
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
    protected String paymentId(long seed) {
        return "ORDTST01%022d".formatted(seed);
    }

    @Override
    protected void givenPayment(String id, long orderNumber, VerifiedStatus status, Money amount) {
        stub.holdsOrder(id, orderNumber, STATUS.get(status), BigDecimal.valueOf(amount.cents(), 2));
    }

    @Override
    protected void providerIsDown() {
        stub.answer(500, "{\"errors\":[{\"code\":\"internal_error\"}]}");
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAnOrder_whenVerified_thenItIsAskedWithTheAccessToken() {
        givenPayment(ORDER_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));

        subject.verify(ORDER_ID);

        assertThat(stub.lastRequest().method()).isEqualTo("GET");
        assertThat(stub.lastRequest().path()).isEqualTo("/v1/orders/" + ORDER_ID);
        assertThat(stub.lastRequest().authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void givenEveryOrderStatus_whenVerified_thenEachMapsToTheStoresStatus() {
        Map<String, VerifiedStatus> expected = Map.ofEntries(
            Map.entry("processed", VerifiedStatus.APPROVED),
            Map.entry("failed", VerifiedStatus.REJECTED), Map.entry("canceled", VerifiedStatus.REJECTED),
            Map.entry("expired", VerifiedStatus.REJECTED),
            Map.entry("created", VerifiedStatus.PENDING), Map.entry("processing", VerifiedStatus.PENDING),
            Map.entry("action_required", VerifiedStatus.PENDING), Map.entry("in_review", VerifiedStatus.PENDING),
            Map.entry("refunded", VerifiedStatus.OTHER), Map.entry("charged_back", VerifiedStatus.OTHER));
        long seed = 100;
        for (var status : expected.entrySet()) {
            String id = paymentId(seed++);
            stub.holdsOrder(id, 1001, status.getKey(), new BigDecimal("21000.5"));

            assertThat(subject.verify(id)).as(status.getKey())
                .isEqualTo(new PaymentVerifier.Verification.Found(new VerifiedPayment(id, 1001, status.getValue(),
                    Money.of(2_100_050))));
        }
    }

    @Test
    void givenAProcessedOrderPartlyRefunded_whenVerified_thenItIsNotAnApproval() {
        stub.holdsOrderJson(ORDER_ID, order(ORDER_ID, "processed", "partially_refunded", "\"1001\"", "\"21000.50\"",
            "ARS"));

        assertThat(subject.verify(ORDER_ID)).isEqualTo(new PaymentVerifier.Verification.Found(
            new VerifiedPayment(ORDER_ID, 1001, VerifiedStatus.OTHER, Money.of(2_100_050))));
    }

    @Test
    void givenAnOrderIdMercadoPagoDoesNotKnow_whenVerified_thenItIsNotFound() {
        // The Orders API answers 400, not 404, for an id it does not know (T-E3-34).
        assertThat(subject.verify(paymentId(424242))).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    void givenAnIdThatIsNotAMercadoPagoOrderId_whenVerified_thenItIsNotFoundWithoutCallingTheProvider() {
        assertThat(subject.verify("../users/me")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(subject.verify("1234567890")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(subject.verify("ordtst01lowercase")).isInstanceOf(PaymentVerifier.Verification.NotFound.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenNoAccessToken_whenAnOrderIsVerified_thenItIsUnavailableWithoutCallingTheProvider() {
        MercadoPagoPaymentVerifier unconfigured = verifier("", Duration.ofSeconds(2));

        assertThat(unconfigured.verify(ORDER_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenTheProviderTakesLongerThanTheReadTimeout_whenVerified_thenItIsUnavailable() {
        givenPayment(ORDER_ID, 1001, VerifiedStatus.APPROVED, Money.of(2_100_050));
        MercadoPagoPaymentVerifier impatient = verifier(TOKEN, Duration.ofMillis(200));
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.verify(ORDER_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenTheProviderIsUnreachable_whenVerified_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.verify(ORDER_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenTheProviderRefusesTheToken_whenVerified_thenItIsUnavailable() {
        stub.answer(401, "{\"errors\":[{\"code\":\"unauthorized\"}]}");

        assertThat(subject.verify(ORDER_ID)).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    @Test
    void givenUnusableOrders_whenVerified_thenTheyAreUnavailable() {
        assertUnavailable(order(ORDER_ID, "processed", "accredited", "\"1001\"", "\"21000.50\"", "USD"), "currency");
        assertUnavailable(order(ORDER_ID, "processed", "accredited", "\"1001\"", "\"21000.505\"", "ARS"), "decimals");
        assertUnavailable(order(ORDER_ID, "processed", "accredited", "\"1001\"", "\"0.00\"", "ARS"), "amount");
        assertUnavailable(order(ORDER_ID, "processed", "accredited", "\"1001\"", "\"many\"", "ARS"), "not a number");
        assertUnavailable(order(ORDER_ID, "chargeback", "accredited", "\"1001\"", "\"21000.50\"", "ARS"), "status");
        assertUnavailable(order("ORDTST01ANOTHERONE", "processed", "accredited", "\"1001\"", "\"21000.50\"", "ARS"),
            "another id");
        assertUnavailable("<html>maintenance</html>", "not json");
    }

    @Test
    void givenAnOrderWhoseReferenceIsNoOrderNumberOfTheStore_whenVerified_thenItIsNotForThisStore() {
        // T-E3-34: another integration of the account, or a test made by hand ("d-1335" came from the sandbox).
        Map<String, String> references = Map.of("no reference", "null", "text", "\"d-1335\"",
            "prefixed", "\"ORDER-1001\"", "zero", "\"0\"", "too long", "\"1234567890123456789\"");
        for (var reference : references.entrySet()) {
            stub.holdsOrderJson(ORDER_ID, order(ORDER_ID, "processed", "accredited", reference.getValue(),
                "\"21000.50\"", "ARS"));

            assertThat(subject.verify(ORDER_ID)).as(reference.getKey())
                .isInstanceOf(PaymentVerifier.Verification.NotForThisStore.class);
        }
    }

    private static String order(String id, String status, String detail, String reference, String amount,
                                String currency) {
        return """
            {"id": "%s", "status": "%s", "status_detail": "%s", "external_reference": %s, "total_amount": %s,
             "currency": "%s"}
            """.formatted(id, status, detail, reference, amount, currency);
    }

    private void assertUnavailable(String json, String why) {
        stub.holdsOrderJson(ORDER_ID, json);

        assertThat(subject.verify(ORDER_ID)).as(why).isInstanceOf(PaymentVerifier.Verification.Unavailable.class);
    }

    private MercadoPagoPaymentVerifier verifier(String token, Duration readTimeout) {
        var properties = new MercadoPagoProperties(stub.baseUrl(), Duration.ofSeconds(1), readTimeout);
        return new MercadoPagoPaymentVerifier(properties, token);
    }
}
