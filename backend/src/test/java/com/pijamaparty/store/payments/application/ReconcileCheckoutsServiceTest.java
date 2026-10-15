package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentApproved;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.PaymentReference;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.MovableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-15 (T-E3b-03): the payment requests whose notification never arrived are asked to MODO every minute. */
class ReconcileCheckoutsServiceTest {

    /** The payment request is created now and expires 10 minutes later. */
    private static final Instant CREATED = Instant.parse("2026-10-15T15:00:00Z");
    private static final PaymentProvider MODO = PaymentProvider.MODO;
    private static final Money TOTAL = Money.of(2_100_050);
    private static final long NUMBER = 1001;
    private static final String REQUEST = "pr-1";

    private final MovableClock clock = new MovableClock(CREATED);
    private final InMemoryPaymentStore payments = new InMemoryPaymentStore();
    private final InMemoryPaymentVerifier mercadoPago = new InMemoryPaymentVerifier(PaymentProvider.MERCADOPAGO);
    private final InMemoryPaymentVerifier modo = new InMemoryPaymentVerifier(MODO, true);
    private final InMemoryPaymentEvents events = new InMemoryPaymentEvents();
    private final ConfirmPaymentService confirmPayment = new ConfirmPaymentService(List.of(mercadoPago, modo),
        new VerifiedPaymentRecorder(new InMemoryPaymentNotifications(), payments, payments, payments, events, clock));
    private final ReconcileCheckoutsService service = new ReconcileCheckoutsService(List.of(mercadoPago, modo),
        payments, confirmPayment, clock);
    private final UUID order = UUID.randomUUID();

    @BeforeEach
    void createTheOrdersPaymentRequest() {
        payments.insertPending(new SavePayments.NewPayment(UUID.randomUUID(), order, NUMBER, MODO, TOTAL, CREATED));
        int attempt = payments.startAttempt(order, MODO, CREATED).orElseThrow().number();
        payments.recordCheckout(order, MODO, attempt, new Checkout(REQUEST,
            URI.create("https://site.modo.com.ar/checkout?id=" + REQUEST),
            Optional.of(CREATED.plus(Duration.ofMinutes(10)))), CREATED);
    }

    @Test
    void givenAnApprovedPaymentWhoseNotificationNeverArrived_whenReconciled_thenTheOrderIsPaidExactlyOnce() {
        modo.holds(paid(VerifiedStatus.APPROVED));
        clock.advance(Duration.ofMinutes(2));

        ReconcileCheckouts.Result first = service.execute();
        clock.advance(Duration.ofMinutes(1));
        ReconcileCheckouts.Result second = service.execute();

        assertThat(first.reconciled()).containsExactly(new ReconcileCheckouts.Reconciled(MODO, REQUEST,
            new ConfirmPayment.Result.Approved(NUMBER)));
        assertThat(second.reconciled()).isEmpty();
        assertThat(events.published()).containsExactly(new PaymentApproved(order, MODO));
        assertThat(payments.paymentOf(order, MODO).orElseThrow().status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void givenARequestNobodyPaid_whenReconciled_thenNothingChanges() {
        clock.advance(Duration.ofMinutes(2));

        ReconcileCheckouts.Result result = service.execute();

        assertThat(result.reconciled()).containsExactly(new ReconcileCheckouts.Reconciled(MODO, REQUEST,
            new ConfirmPayment.Result.NotFoundAtProvider()));
        assertThat(payments.paymentOf(order, MODO).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void givenARejectedPayment_whenReconciledAgain_thenTheRejectionIsNotProcessedTwice() {
        modo.holds(paid(VerifiedStatus.REJECTED));
        clock.advance(Duration.ofMinutes(2));
        service.execute();
        clock.advance(Duration.ofMinutes(1));

        ReconcileCheckouts.Result again = service.execute();

        assertThat(again.reconciled()).extracting(ReconcileCheckouts.Reconciled::result)
            .containsExactly(new ConfirmPayment.Result.AlreadyProcessed());
        assertThat(payments.paymentOf(order, MODO).orElseThrow().status()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void givenARequestCreatedLessThanAMinuteAgo_whenReconciled_thenMODOIsNotAsked() {
        modo.holds(paid(VerifiedStatus.APPROVED));
        clock.advance(Duration.ofSeconds(30));

        assertThat(service.execute().reconciled()).isEmpty();
        assertThat(modo.lookups()).isEmpty();
    }

    @Test
    void givenARequestExpiredMoreThan15MinutesAgo_whenReconciled_thenMODOIsNotAsked() {
        modo.holds(paid(VerifiedStatus.APPROVED));
        clock.advance(Duration.ofMinutes(26));

        assertThat(service.execute().reconciled()).isEmpty();
        assertThat(modo.lookups()).isEmpty();
    }

    @Test
    void givenNoProviderVerifiesByCheckout_whenReconciled_thenNothingIsAsked() {
        modo.holds(paid(VerifiedStatus.APPROVED));
        clock.advance(Duration.ofMinutes(2));
        var withoutModo = new ReconcileCheckoutsService(List.of(mercadoPago), payments, confirmPayment, clock);

        assertThat(withoutModo.execute().reconciled()).isEmpty();
        assertThat(modo.lookups()).isEmpty();
    }

    @Test
    void givenMoreThan50RequestsDue_whenReconciled_thenOnly50AreAskedAndTheRestWaitForTheNextRun() {
        for (int each = 1; each <= ReconcileCheckoutsService.MAX_PER_RUN; each++) {
            UUID other = UUID.randomUUID();
            payments.insertPending(new SavePayments.NewPayment(UUID.randomUUID(), other, NUMBER + each, MODO, TOTAL,
                CREATED));
            int attempt = payments.startAttempt(other, MODO, CREATED.plusSeconds(each)).orElseThrow().number();
            payments.recordCheckout(other, MODO, attempt, new Checkout("pr-other-" + each,
                URI.create("https://site.modo.com.ar/checkout?id=pr-other-" + each),
                Optional.of(CREATED.plus(Duration.ofMinutes(10)))), CREATED.plusSeconds(each));
        }
        clock.advance(Duration.ofMinutes(2));

        ReconcileCheckouts.Result result = service.execute();

        assertThat(result.reconciled()).hasSize(ReconcileCheckoutsService.MAX_PER_RUN);
        assertThat(ReconcileCheckoutsService.MAX_PER_RUN).isEqualTo(50);
        assertThat(modo.lookups()).hasSize(50).first().isEqualTo(REQUEST);
    }

    private static VerifiedPayment paid(VerifiedStatus status) {
        return new VerifiedPayment(REQUEST, new PaymentReference.ByCheckout(REQUEST), status, TOTAL);
    }
}
