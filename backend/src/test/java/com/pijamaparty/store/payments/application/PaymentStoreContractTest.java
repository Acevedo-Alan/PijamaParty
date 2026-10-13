package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadPayments} and {@link SavePayments} (UC-14): one payment per order and provider; and of
 * {@link PaymentsToConfirm} and {@link PaymentConfirmations} (UC-15): found by order number, an approval is final.
 */
public abstract class PaymentStoreContractTest<
    T extends LoadPayments & SavePayments & PaymentsToConfirm & PaymentConfirmations> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    protected static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;
    protected static final Checkout CHECKOUT =
        new Checkout("123-pref", URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123-pref"));
    private static final Checkout OTHER_CHECKOUT =
        new Checkout("456-pref", URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=456-pref"));

    protected final UUID order = UUID.randomUUID();
    private final Map<UUID, Long> numbers = new HashMap<>();

    protected SavePayments.NewPayment pendingPayment(UUID orderId) {
        long number = numbers.computeIfAbsent(orderId, id -> 1001L + numbers.size());
        return new SavePayments.NewPayment(UUID.randomUUID(), orderId, number, MERCADOPAGO, Money.of(2_100_050), NOW);
    }

    @Test
    void givenNoPayment_whenLookingForItsCheckout_thenThereIsNone() {
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenAPendingPayment_whenLookingForItsCheckoutBeforeItIsRecorded_thenThereIsNone() {
        subject.insertPending(pendingPayment(order));
        persistAndForget();

        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenAPendingPayment_whenItsCheckoutIsRecorded_thenItIsTheOrdersCheckout() {
        subject.insertPending(pendingPayment(order));

        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, CHECKOUT, NOW.plusSeconds(1));
        persistAndForget();

        assertThat(recorded).isTrue();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenAnOrderWithAPayment_whenAnotherPaymentOfTheSameProviderIsInserted_thenNothingChanges() {
        subject.insertPending(pendingPayment(order));
        subject.recordCheckout(order, MERCADOPAGO, CHECKOUT, NOW);

        subject.insertPending(pendingPayment(order));
        persistAndForget();

        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenAPaymentWithACheckout_whenAnotherCheckoutIsRecorded_thenTheFirstStays() {
        subject.insertPending(pendingPayment(order));
        subject.recordCheckout(order, MERCADOPAGO, CHECKOUT, NOW);

        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, OTHER_CHECKOUT, NOW.plusSeconds(1));
        persistAndForget();

        assertThat(recorded).isFalse();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).contains(CHECKOUT);
    }

    @Test
    void givenNoPayment_whenACheckoutIsRecorded_thenNothingIsRecorded() {
        boolean recorded = subject.recordCheckout(order, MERCADOPAGO, CHECKOUT, NOW);
        persistAndForget();

        assertThat(recorded).isFalse();
        assertThat(subject.checkoutOf(order, MERCADOPAGO)).isEmpty();
    }

    @Test
    void givenTwoOrders_whenOneRecordsItsCheckout_thenTheOtherStillHasNone() {
        UUID other = UUID.randomUUID();
        subject.insertPending(pendingPayment(order));
        subject.insertPending(pendingPayment(other));

        subject.recordCheckout(order, MERCADOPAGO, CHECKOUT, NOW);
        persistAndForget();

        assertThat(subject.checkoutOf(other, MERCADOPAGO)).isEmpty();
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
}
