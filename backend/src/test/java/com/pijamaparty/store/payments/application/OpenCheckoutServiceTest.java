package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.payments.api.CheckoutOpening;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.MovableClock;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenCheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final PaymentProvider MERCADOPAGO = PaymentProvider.MERCADOPAGO;
    private static final PaymentProvider MODO = PaymentProvider.MODO;

    private final InMemoryPaymentStore payments = new InMemoryPaymentStore();
    private final InMemoryPaymentGateway gateway = new InMemoryPaymentGateway(MERCADOPAGO);
    private final InMemoryPaymentGateway modo = new InMemoryPaymentGateway(MODO);
    private final UUID paymentId = UUID.randomUUID();
    private final MovableClock clock = new MovableClock(NOW);
    private final OpenCheckoutService service = new OpenCheckoutService(payments, payments, List.of(gateway, modo),
        () -> paymentId, clock);
    private final CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), 1001, Money.of(2_100_050),
        MERCADOPAGO, new CheckoutRequest.Payer("Ana Pérez", "ana@example.com"), NOW.plusSeconds(1800));
    private final CheckoutRequest modoRequest = new CheckoutRequest(UUID.randomUUID(), 1002, Money.of(2_100_050),
        MODO, new CheckoutRequest.Payer("Ana Pérez", "ana@example.com"), NOW.plusSeconds(1800));

    @Test
    void givenAPlacedOrder_whenItsPaymentIsOpened_thenThePendingPaymentHoldsTheProvidersCheckout() {
        CheckoutOpening opening = service.open(request);

        assertThat(opening).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(payments.paymentOf(request.orderId(), MERCADOPAGO)).contains(new InMemoryPaymentStore.StoredPayment(
            paymentId, 1001, Money.of(2_100_050), Optional.of(
                new Checkout("checkout-1", URI.create("https://pay.example.com/checkout-1"))),
            PaymentStatus.PENDING, Optional.empty()));
        assertThat(gateway.requests()).containsExactly(request);
    }

    @Test
    void givenAPlacedOrder_whenItsPaymentIsOpened_thenTheAttemptIsRecordedBeforeTheProviderIsCalled() {
        AtomicReference<List<InMemoryPaymentStore.StoredAttempt>> seenByTheProvider = new AtomicReference<>();
        PaymentGateway observing = new PaymentGateway() {
            @Override
            public PaymentProvider provider() {
                return MERCADOPAGO;
            }

            @Override
            public Outcome createCheckout(CheckoutRequest ignored, CheckoutAttempt attempt) {
                seenByTheProvider.set(payments.attemptsOf(request.orderId(), MERCADOPAGO));
                return new Outcome.Unavailable();
            }
        };

        new OpenCheckoutService(payments, payments, List.of(observing), UUID::randomUUID, clock).open(request);

        assertThat(seenByTheProvider.get()).extracting(InMemoryPaymentStore.StoredAttempt::attempt)
            .containsExactly(new CheckoutAttempt(1, "1001-1"));
    }

    @Test
    void givenAPaymentWithACheckout_whenItIsOpenedAgain_thenTheSameCheckoutIsAnsweredWithoutCallingTheProvider() {
        service.open(request);

        CheckoutOpening again = service.open(request);

        assertThat(again).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(gateway.requests()).hasSize(1);
        assertThat(payments.size()).isOne();
        assertThat(payments.attemptsOf(request.orderId(), MERCADOPAGO)).hasSize(1);
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
    void givenAPaymentLeftWithoutACheckout_whenItIsOpenedAgainWithTheProviderBack_thenANewAttemptGetsTheCheckout() {
        gateway.down(true);
        service.open(request);
        gateway.down(false);

        CheckoutOpening retry = service.open(request);

        assertThat(retry).isInstanceOf(CheckoutOpening.Opened.class);
        assertThat(payments.size()).isOne();
        assertThat(payments.checkoutOf(request.orderId(), MERCADOPAGO)).isPresent();
        assertThat(gateway.attempts()).extracting(CheckoutAttempt::number).containsExactly(1, 2);
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
            public Outcome createCheckout(CheckoutRequest ignored, CheckoutAttempt attempt) {
                // The concurrent call's own attempt, the second, records its checkout first.
                CheckoutAttempt concurrent = payments.startAttempt(request.orderId(), MERCADOPAGO, NOW).orElseThrow();
                payments.recordCheckout(request.orderId(), MERCADOPAGO, concurrent.number(), first, NOW);
                return new Outcome.Created(new Checkout("second", URI.create("https://pay.example.com/second")));
            }
        };
        var raced = new OpenCheckoutService(payments, payments, List.of(racing), UUID::randomUUID, clock);

        assertThat(raced.open(request)).isEqualTo(new CheckoutOpening.Opened(first.url()));
        assertThat(payments.checkoutOf(request.orderId(), MERCADOPAGO)).contains(first);
    }

    @Test
    void givenAModoOrder_whenItsPaymentIsOpened_thenTheAttemptsReferenceGoesToTheProviderAndTheExpiryIsKept() {
        modo.expiringAt(NOW.plusSeconds(600));

        CheckoutOpening opening = service.open(modoRequest);

        assertThat(opening).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(modo.attempts()).containsExactly(new CheckoutAttempt(1, "1002-1"));
        assertThat(payments.checkoutOf(modoRequest.orderId(), MODO))
            .hasValueSatisfying(checkout -> assertThat(checkout.expiresAt()).contains(NOW.plusSeconds(600)));
    }

    @Test
    void givenAModoPaymentRequestThatExpiredWhileTheReservationLasts_whenPagarIsTapped_thenANewOneForTheSameAmount() {
        modo.expiringAt(NOW.plusSeconds(600));
        service.open(modoRequest);
        clock.advance(Duration.ofMinutes(11));
        modo.expiringAt(clock.instant().plusSeconds(600));

        CheckoutOpening retry = service.open(modoRequest);

        assertThat(retry).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-2")));
        assertThat(modo.attempts()).containsExactly(new CheckoutAttempt(1, "1002-1"), new CheckoutAttempt(2, "1002-2"));
        assertThat(modo.requests()).extracting(CheckoutRequest::amount)
            .containsExactly(Money.of(2_100_050), Money.of(2_100_050));
        assertThat(payments.checkoutOf(modoRequest.orderId(), MODO)).map(Checkout::id).contains("checkout-2");
        assertThat(payments.size()).isOne();
    }

    @Test
    void givenAModoPaymentRequestWithMoreThanAMinuteLeft_whenPagarIsTapped_thenTheSameOneIsAnswered() {
        modo.expiringAt(NOW.plusSeconds(600));
        service.open(modoRequest);
        clock.advance(Duration.ofMinutes(8));

        CheckoutOpening retry = service.open(modoRequest);

        assertThat(retry).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-1")));
        assertThat(modo.attempts()).hasSize(1);
    }

    @Test
    void givenAModoPaymentRequestAboutToExpire_whenPagarIsTapped_thenANewOneIsCreated() {
        modo.expiringAt(NOW.plusSeconds(600));
        service.open(modoRequest);
        clock.advance(Duration.ofSeconds(540));

        CheckoutOpening retry = service.open(modoRequest);

        assertThat(retry).isEqualTo(new CheckoutOpening.Opened(URI.create("https://pay.example.com/checkout-2")));
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
