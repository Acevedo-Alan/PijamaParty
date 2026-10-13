package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.payments.api.CheckoutOpening;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenCheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;

    private final InMemoryPaymentStore payments = new InMemoryPaymentStore();
    private final InMemoryPaymentGateway gateway = new InMemoryPaymentGateway(MERCADOPAGO);
    private final UUID paymentId = UUID.randomUUID();
    private final OpenCheckoutService service = new OpenCheckoutService(payments, payments, List.of(gateway),
        () -> paymentId, Clock.fixed(NOW, ZoneOffset.UTC));
    private final CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), 1001, Money.of(2_100_050),
        MERCADOPAGO, new CheckoutRequest.Payer("Ana Pérez", "ana@example.com"), NOW.plusSeconds(1800));

    @Test
    void givenAPlacedOrder_whenItsPaymentIsOpened_thenThePendingPaymentHoldsTheProvidersCheckout() {
        CheckoutOpening opening = service.open(request);

        assertThat(opening).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(payments.paymentOf(request.orderId(), MERCADOPAGO)).contains(new InMemoryPaymentStore.StoredPayment(
            paymentId, Money.of(2_100_050), Optional.of(
                new Checkout("checkout-1", URI.create("https://pay.example.com/checkout-1")))));
        assertThat(gateway.requests()).containsExactly(request);
    }

    @Test
    void givenAPaymentWithACheckout_whenItIsOpenedAgain_thenTheSameCheckoutIsAnsweredWithoutCallingTheProvider() {
        service.open(request);

        CheckoutOpening again = service.open(request);

        assertThat(again).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(gateway.requests()).hasSize(1);
        assertThat(payments.size()).isOne();
    }

    @Test
    void givenTheProviderDown_whenThePaymentIsOpened_thenItIsUnavailableAndThePendingPaymentWaitsForARetry() {
        gateway.down(true);

        CheckoutOpening opening = service.open(request);

        assertThat(opening).isInstanceOf(CheckoutOpening.ProviderUnavailable.class);
        assertThat(payments.paymentOf(request.orderId(), MERCADOPAGO)).hasValueSatisfying(payment ->
            assertThat(payment.checkout()).isEmpty());
    }

    @Test
    void givenAPaymentLeftWithoutACheckout_whenItIsOpenedAgainWithTheProviderBack_thenTheCheckoutIsCreated() {
        gateway.down(true);
        service.open(request);
        gateway.down(false);

        CheckoutOpening retry = service.open(request);

        assertThat(retry).isInstanceOf(CheckoutOpening.Opened.class);
        assertThat(payments.size()).isOne();
        assertThat(payments.checkoutOf(request.orderId(), MERCADOPAGO)).isPresent();
    }

    @Test
    void givenAConcurrentCallRecordedItsCheckoutFirst_whenThisOneFinishes_thenTheFirstCheckoutIsAnswered() {
        Checkout first = new Checkout("first", URI.create("https://pay.example.com/first"));
        PaymentGateway racing = new PaymentGateway() {
            @Override
            public PaymentProvider provider() {
                return MERCADOPAGO;
            }

            @Override
            public Outcome createCheckout(CheckoutRequest ignored) {
                payments.recordCheckout(request.orderId(), MERCADOPAGO, first, NOW);
                return new Outcome.Created(new Checkout("second", URI.create("https://pay.example.com/second")));
            }
        };
        var raced = new OpenCheckoutService(payments, payments, List.of(racing), UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(raced.open(request)).isEqualTo(new CheckoutOpening.Opened(first.url()));
        assertThat(payments.checkoutOf(request.orderId(), MERCADOPAGO)).contains(first);
    }

    @Test
    void givenTwoGatewaysForOneProvider_whenTheServiceIsBuilt_thenItRefuses() {
        assertThatThrownBy(() -> new OpenCheckoutService(payments, payments,
            List.of(gateway, new InMemoryPaymentGateway(MERCADOPAGO)), UUID::randomUUID, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenNoGatewayForTheProvider_whenThePaymentIsOpened_thenItFailsWithoutStoringAPayment() {
        var withoutGateways = new OpenCheckoutService(payments, payments, List.of(), UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> withoutGateways.open(request)).isInstanceOf(IllegalStateException.class);
        assertThat(payments.size()).isZero();
    }
}
