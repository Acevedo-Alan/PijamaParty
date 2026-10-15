package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadPayments} and {@link SavePayments} (UC-14): one payment per order and provider, its checkout
 * attempts numbered one after another and the latest checkout recorded the current one (T-E3b-02); and of
 * {@link PaymentsToConfirm} and {@link PaymentConfirmations} (UC-15): found by order number, an approval is final; and
 * of {@link PaymentsByCheckout} and {@link CheckoutsToReconcile} (T-E3b-03): found by any of its checkouts, and the
 * recent checkouts of a payment without approval due for reconciliation.
 */
public abstract class PaymentStoreContractTest<
    T extends LoadPayments & SavePayments & PaymentsToConfirm & PaymentConfirmations & PaymentsByCheckout
        & CheckoutsToReconcile> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    protected static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;
    protected static final PaymentProvider MODO = PaymentProvider.MODO;
    /** The statuses the reconciliation asks for. */
    private static final Set<PaymentStatus> OPEN = Set.of(PaymentStatus.PENDING, PaymentStatus.REJECTED);
    protected static final Checkout CHECKOUT =
        new Checkout("123-pref", URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123-pref"));
    private static final Checkout OTHER_CHECKOUT =
        new Checkout("456-pref", URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=456-pref"));
    private static final Checkout MODO_REQUEST = new Checkout("modo-1",
        URI.create("https://site.modo.com.ar/checkout?id=modo-1"), Optional.of(NOW.plusSeconds(600)));
    private static final Checkout LATER_MODO_REQUEST = new Checkout("modo-2",
        URI.create("https://site.modo.com.ar/checkout?id=modo-2"), Optional.of(NOW.plusSeconds(1200)));

    protected final UUID order = UUID.randomUUID();
    private final Map<UUID, Long> numbers = new HashMap<>();

    protected SavePayments.NewPayment pendingPayment(UUID orderId) {
        return pendingPayment(orderId, MERCADOPAGO);
    }

    protected SavePayments.NewPayment pendingPayment(UUID orderId, PaymentProvider provider) {
        long number = numbers.computeIfAbsent(orderId, id -> 1001L + numbers.size());
        return new SavePayments.NewPayment(UUID.randomUUID(), orderId, number, provider, Money.of(2_100_050), NOW);
    }

    /** The order's payment with the provider and its first attempt. */
    protected CheckoutAttempt paymentWithAnAttempt(UUID orderId, PaymentProvider provider) {
        subject.insertPending(pendingPayment(orderId, provider));
        return subject.startAttempt(orderId, provider, NOW).orElseThrow();
    }

    @Test
    void givenNoPayment_whenLookingForItsCheckout_thenThereIsNone() {
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenAPendingPayment_whenLookingForItsCheckoutBeforeItIsRecorded_thenThereIsNone() {
        paymentWithAnAttempt(order, MERCADOPAGO);
        persistAndForget();

        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenAPendingPayment_whenItsCheckoutIsRecorded_thenItIsTheOrdersCheckout() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MERCADOPAGO);

        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, attempt.number(), CHECKOUT, NOW.plusSeconds(1));
        persistAndForget();

        assertThat(recorded).isTrue();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenACheckoutThatExpires_whenItIsRecorded_thenItIsLoadedWithItsExpiry() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);

        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        persistAndForget();

        assertThat(subject.checkoutOf(order, MODO)).contains(MODO_REQUEST);
    }

    @Test
    void givenAnOrderWithAPayment_whenAnotherPaymentOfTheSameProviderIsInserted_thenNothingChanges() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MERCADOPAGO);
        subject.recordCheckout(order, MERCADOPAGO, attempt.number(), CHECKOUT, NOW);

        subject.insertPending(pendingPayment(order));
        persistAndForget();

        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenAnAttemptWithACheckout_whenAnotherCheckoutIsRecordedInIt_thenTheFirstStays() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MERCADOPAGO);
        subject.recordCheckout(order, MERCADOPAGO, attempt.number(), CHECKOUT, NOW);

        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, attempt.number(), OTHER_CHECKOUT,
            NOW.plusSeconds(1));
        persistAndForget();

        assertThat(recorded).isFalse();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenNoPayment_whenACheckoutIsRecorded_thenNothingIsRecorded() {
        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, 1, CHECKOUT, NOW);
        persistAndForget();

        assertThat(recorded).isFalse();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenTwoOrders_whenOneRecordsItsCheckout_thenTheOtherStillHasNone() {
        UUID other = UUID.randomUUID();
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MERCADOPAGO);
        paymentWithAnAttempt(other, MERCADOPAGO);

        subject.recordCheckout(order, MERCADOPAGO, attempt.number(), CHECKOUT, NOW);
        persistAndForget();

        assertThat(subject.checkoutOf(other, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenNoPayment_whenAnAttemptIsStarted_thenThereIsNone() {
        assertThat(subject.startAttempt(order, MODO, NOW)).isEmpty();
    }

    @Test
    void givenAPayment_whenAttemptsAreStarted_thenTheyAreNumberedWithTheOrderNumberInTheirReference() {
        SavePayments.NewPayment payment = pendingPayment(order, MODO);
        subject.insertPending(payment);

        Optional<CheckoutAttempt> first = subject.startAttempt(order, MODO, NOW);
        Optional<CheckoutAttempt> second = subject.startAttempt(order, MODO, NOW.plusSeconds(600));
        persistAndForget();

        assertThat(first).contains(new CheckoutAttempt(1, payment.orderNumber() + "-1"));
        assertThat(second).contains(new CheckoutAttempt(2, payment.orderNumber() + "-2"));
    }

    @Test
    void givenACurrentCheckout_whenALaterAttemptRecordsOne_thenTheLaterIsTheCurrent() {
        CheckoutAttempt first = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, first.number(), MODO_REQUEST, NOW);
        CheckoutAttempt second = subject.startAttempt(order, MODO, NOW.plusSeconds(600)).orElseThrow();

        boolean current = subject.recordCheckout(order, MODO, second.number(), LATER_MODO_REQUEST,
            NOW.plusSeconds(601));
        persistAndForget();

        assertThat(current).isTrue();
        assertThat(subject.checkoutOf(order, MODO)).contains(LATER_MODO_REQUEST);
    }

    @Test
    void givenALaterAttemptWithACheckout_whenAnEarlierOneRecordsItsOwn_thenTheLaterStaysTheCurrent() {
        CheckoutAttempt first = paymentWithAnAttempt(order, MODO);
        CheckoutAttempt second = subject.startAttempt(order, MODO, NOW).orElseThrow();
        subject.recordCheckout(order, MODO, second.number(), LATER_MODO_REQUEST, NOW);

        boolean current = subject.recordCheckout(order, MODO, first.number(), MODO_REQUEST, NOW.plusSeconds(1));
        persistAndForget();

        assertThat(current).isFalse();
        assertThat(subject.checkoutOf(order, MODO)).contains(LATER_MODO_REQUEST);
    }

    @Test
    void givenACheckoutRecordedInOneAttempt_whenAnotherAttemptGetsTheSameCheckout_thenItIsNotRecordedAgain() {
        CheckoutAttempt first = paymentWithAnAttempt(order, MERCADOPAGO);
        CheckoutAttempt second = subject.startAttempt(order, MERCADOPAGO, NOW).orElseThrow();
        subject.recordCheckout(order, MERCADOPAGO, first.number(), CHECKOUT, NOW);

        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, second.number(), CHECKOUT, NOW.plusSeconds(1));
        persistAndForget();

        assertThat(recorded).isFalse();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenAPendingPayment_whenLockedByItsOrderNumber_thenItIsFoundPendingWithTheOrdersAmount() {
        SavePayments.NewPayment payment = pendingPayment(order);
        subject.insertPending(payment);
        persistAndForget();

        assertThat(subject.lock(MERCADOPAGO, payment.orderNumber())).contains(new PaymentsToConfirm.PaymentToConfirm(
            order, payment.orderNumber(), PaymentStatus.PENDING, Money.of(2_100_050)));
    }

    @Test
    void givenNoPaymentOfTheOrderNumber_whenLocked_thenNothingIsFound() {
        subject.insertPending(pendingPayment(order));
        persistAndForget();

        assertThat(subject.lock(MERCADOPAGO, 999_999)).isEmpty();
    }

    @Test
    void givenAPendingPayment_whenRejectedAndThenApproved_thenItEndsApproved() {
        SavePayments.NewPayment payment = pendingPayment(order);
        subject.insertPending(payment);

        boolean rejected = subject.record(order, MERCADOPAGO, PaymentStatus.REJECTED, "111", NOW);
        boolean approved = subject.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "222", NOW.plusSeconds(60));
        persistAndForget();

        assertThat(rejected).isTrue();
        assertThat(approved).isTrue();
        assertThat(subject.lock(MERCADOPAGO, payment.orderNumber()))
            .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(PaymentStatus.APPROVED));
    }

    @Test
    void givenAnApprovedPayment_whenAnotherVerificationIsRecorded_thenItStaysApproved() {
        SavePayments.NewPayment payment = pendingPayment(order);
        subject.insertPending(payment);
        subject.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "222", NOW);

        boolean rejected = subject.record(order, MERCADOPAGO, PaymentStatus.REJECTED, "333", NOW.plusSeconds(60));
        boolean approvedAgain = subject.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "222", NOW.plusSeconds(60));
        persistAndForget();

        assertThat(rejected).isFalse();
        assertThat(approvedAgain).isFalse();
        assertThat(subject.lock(MERCADOPAGO, payment.orderNumber()))
            .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(PaymentStatus.APPROVED));
    }

    @Test
    void givenNoPayment_whenAVerificationIsRecorded_thenNothingIsRecorded() {
        assertThat(subject.record(order, MERCADOPAGO, PaymentStatus.APPROVED, "222", NOW)).isFalse();
    }

    @Test
    void givenAModoPaymentWithTwoRequests_whenEachIsLookedUp_thenBothLeadToTheOrder() {
        SavePayments.NewPayment payment = pendingPayment(order, MODO);
        subject.insertPending(payment);
        int first = subject.startAttempt(order, MODO, NOW).orElseThrow().number();
        int second = subject.startAttempt(order, MODO, NOW.plusSeconds(700)).orElseThrow().number();
        subject.recordCheckout(order, MODO, first, MODO_REQUEST, NOW);
        subject.recordCheckout(order, MODO, second, LATER_MODO_REQUEST, NOW.plusSeconds(700));
        persistAndForget();

        assertThat(subject.orderNumberOf(MODO, "modo-1")).isEqualTo(OptionalLong.of(payment.orderNumber()));
        assertThat(subject.orderNumberOf(MODO, "modo-2")).isEqualTo(OptionalLong.of(payment.orderNumber()));
    }

    @Test
    void givenACheckoutThisStoreDidNotCreate_whenLookedUp_thenNoOrderIsFound() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        persistAndForget();

        assertThat(subject.orderNumberOf(MODO, "modo-unknown")).isEmpty();
        assertThat(subject.orderNumberOf(MERCADOPAGO, "modo-1")).isEmpty();
    }

    @Test
    void givenARecentModoRequestOfAPendingPayment_whenTheReconciliationLooks_thenItIsDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        persistAndForget();

        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 50))
            .containsExactly(new CheckoutsToReconcile.CheckoutToReconcile(MODO, "modo-1"));
    }

    @Test
    void givenAModoRequestOutsideTheWindow_whenTheReconciliationLooks_thenItIsNotDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        persistAndForget();

        // Created at NOW, which is not before NOW; and expired at NOW + 600 s, which is not after the window start
        assertThat(subject.due(Set.of(MODO), OPEN, NOW, NOW.minusSeconds(900), 50)).isEmpty();
        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(1600), NOW.plusSeconds(600), 50)).isEmpty();
    }

    @Test
    void givenARejectedPayment_whenTheReconciliationLooks_thenItsRequestIsStillDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        subject.record(order, MODO, PaymentStatus.REJECTED, "modo-1", NOW.plusSeconds(30));
        persistAndForget();

        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 50)).hasSize(1);
    }

    @Test
    void givenARejectedPayment_whenTheReconciliationLooksOnlyForPendingOnes_thenItsRequestIsNotDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        subject.record(order, MODO, PaymentStatus.REJECTED, "modo-1", NOW.plusSeconds(30));
        persistAndForget();

        assertThat(subject.due(Set.of(MODO), Set.of(PaymentStatus.PENDING), NOW.plusSeconds(60), NOW.minusSeconds(900),
            50)).isEmpty();
    }

    @Test
    void givenAnApprovedPayment_whenTheReconciliationLooks_thenNoneOfItsRequestsIsDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        subject.record(order, MODO, PaymentStatus.APPROVED, "modo-1", NOW.plusSeconds(30));
        persistAndForget();

        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 50)).isEmpty();
    }

    @Test
    void givenACheckoutThatLastsAsLongAsTheReservation_whenTheReconciliationLooks_thenItIsNeverDue() {
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MERCADOPAGO);
        subject.recordCheckout(order, MERCADOPAGO, attempt.number(), CHECKOUT, NOW);
        persistAndForget();

        assertThat(subject.due(Set.of(MERCADOPAGO, MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 50)).isEmpty();
    }

    @Test
    void givenTheRequestsOfTwoPayments_whenTheReconciliationLooksForOne_thenTheOldestIsDue() {
        UUID later = UUID.randomUUID();
        subject.insertPending(pendingPayment(later, MODO));
        int laterAttempt = subject.startAttempt(later, MODO, NOW.plusSeconds(10)).orElseThrow().number();
        subject.recordCheckout(later, MODO, laterAttempt, LATER_MODO_REQUEST, NOW.plusSeconds(10));
        CheckoutAttempt attempt = paymentWithAnAttempt(order, MODO);
        subject.recordCheckout(order, MODO, attempt.number(), MODO_REQUEST, NOW);
        persistAndForget();

        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 1))
            .containsExactly(new CheckoutsToReconcile.CheckoutToReconcile(MODO, "modo-1"));
        assertThat(subject.due(Set.of(MODO), OPEN, NOW.plusSeconds(60), NOW.minusSeconds(900), 50)).hasSize(2);
    }
}
