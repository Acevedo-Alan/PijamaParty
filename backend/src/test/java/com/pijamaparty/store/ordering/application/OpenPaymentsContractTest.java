package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.OpenPayments.Opening;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link OpenPayments} (UC-14): MercadoPago is paid on the store's page (T-E3-29); MODO gets one checkout
 * per order, answered again on a retry.
 */
public abstract class OpenPaymentsContractTest<T extends OpenPayments> extends ContractTest<T> {

    private static final AtomicLong NUMBERS = new AtomicLong(5000);

    /** Makes MODO unreachable or failing for the next calls. */
    protected abstract void providerIsDown();

    /** Brings MODO back. */
    protected abstract void providerIsBack();

    protected OrderToPay orderToPay(PaymentProvider provider) {
        return new OrderToPay(OrderId.of(UUID.randomUUID()), NUMBERS.incrementAndGet(), OrderStatus.AWAITING_PAYMENT,
            Money.of(4_450_000), provider, "Ana Pérez", "ana@example.com", Instant.now().plusSeconds(1800));
    }

    @Test
    void givenAMercadoPagoOrder_whenItsPaymentIsOpened_thenItIsPaidOnTheStoresPage() {
        OrderToPay order = orderToPay(PaymentProvider.MERCADOPAGO);

        assertThat(subject.open(order)).isEqualTo(new Opening.OnSite());
        assertThat(subject.open(order)).isEqualTo(new Opening.OnSite());
    }

    @Test
    void givenAModoOrder_whenItsPaymentIsOpened_thenTheProvidersHttpsCheckoutIsAnswered() {
        assertThat(subject.open(orderToPay(PaymentProvider.MODO))).isInstanceOfSatisfying(Opening.Redirect.class,
            redirect -> assertThat(redirect.paymentUrl().getScheme()).isEqualTo("https"));
    }

    @Test
    void givenAModoOrderWhosePaymentWasOpened_whenItIsOpenedAgain_thenTheSameCheckoutIsAnswered() {
        OrderToPay order = orderToPay(PaymentProvider.MODO);

        Opening first = subject.open(order);

        assertThat(subject.open(order)).isEqualTo(first).isInstanceOf(Opening.Redirect.class);
    }

    @Test
    void givenTwoModoOrders_whenTheirPaymentsAreOpened_thenEachHasItsOwnCheckout() {
        assertThat(subject.open(orderToPay(PaymentProvider.MODO)))
            .isNotEqualTo(subject.open(orderToPay(PaymentProvider.MODO)));
    }

    @Test
    void givenModoDown_whenThePaymentIsOpened_thenThereIsNoCheckoutAndARetryCanOpenIt() {
        OrderToPay order = orderToPay(PaymentProvider.MODO);
        providerIsDown();

        assertThat(subject.open(order)).isEqualTo(new Opening.Unavailable());
        providerIsBack();
        assertThat(subject.open(order)).isInstanceOf(Opening.Redirect.class);
    }
}
