package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Contract of {@link OpenPayments} (UC-14): one checkout per order, answered again on a retry. */
public abstract class OpenPaymentsContractTest<T extends OpenPayments> extends ContractTest<T> {

    private static final AtomicLong NUMBERS = new AtomicLong(5000);

    /** Makes the provider unreachable or failing for the next calls. */
    protected abstract void providerIsDown();

    /** Brings the provider back. */
    protected abstract void providerIsBack();

    protected OrderToPay orderToPay() {
        return new OrderToPay(OrderId.of(UUID.randomUUID()), NUMBERS.incrementAndGet(), OrderStatus.AWAITING_PAYMENT,
            Money.of(4_450_000), PaymentProvider.MERCADOPAGO, "Ana Pérez", "ana@example.com",
            Instant.now().plusSeconds(1800));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenItsPaymentIsOpened_thenTheProvidersHttpsCheckoutIsAnswered() {
        Optional<URI> url = subject.open(orderToPay());

        assertThat(url).hasValueSatisfying(checkout -> assertThat(checkout.getScheme()).isEqualTo("https"));
    }

    @Test
    void givenAnOrderWhosePaymentWasOpened_whenItIsOpenedAgain_thenTheSameCheckoutIsAnswered() {
        OrderToPay order = orderToPay();
        Optional<URI> first = subject.open(order);

        assertThat(subject.open(order)).isEqualTo(first).isPresent();
    }

    @Test
    void givenTwoOrders_whenTheirPaymentsAreOpened_thenEachHasItsOwnCheckout() {
        assertThat(subject.open(orderToPay())).isNotEqualTo(subject.open(orderToPay()));
    }

    @Test
    void givenTheProviderDown_whenThePaymentIsOpened_thenThereIsNoCheckoutAndARetryCanOpenIt() {
        OrderToPay order = orderToPay();
        providerIsDown();

        assertThat(subject.open(order)).isEmpty();

        providerIsBack();
        assertThat(subject.open(order)).isPresent();
    }
}
