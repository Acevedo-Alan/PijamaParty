package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetryOrderPaymentServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-13T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryOpenPayments payments = new InMemoryOpenPayments();
    private final TrackingToken token = new TrackingToken("token-" + UUID.randomUUID());

    private RetryOrderPayment.Result retryAt(Instant now) {
        var openOrderPayment = new OpenOrderPaymentService(orders, payments, Clock.fixed(now, ZoneOffset.UTC));
        return new RetryOrderPaymentService(orders, openOrderPayment).execute(new RetryOrderPayment.Command(token));
    }

    @Test
    void givenAnOrderWhosePaymentFailedToOpen_whenRetried_thenThePaymentIsOpenedForItsTotal() {
        NewOrder order = placed();
        payments.down(true);
        retryAt(PLACED_AT);
        payments.down(false);

        var result = retryAt(PLACED_AT.plusSeconds(60));

        assertThat(result).isEqualTo(new RetryOrderPayment.Result.Opened(
            URI.create("https://pay.example.com/" + order.number())));
        assertThat(payments.opened()).last().satisfies(toPay -> {
            assertThat(toPay.id()).isEqualTo(order.id());
            assertThat(toPay.total()).isEqualTo(Money.of(4_450_000));
        });
    }

    @Test
    void givenAnOrderWithAnOpenPayment_whenRetried_thenTheSameCheckoutIsAnswered() {
        placed();
        var first = retryAt(PLACED_AT.plusSeconds(1));

        assertThat(retryAt(PLACED_AT.plusSeconds(60))).isEqualTo(first);
    }

    @Test
    void givenTheProviderDown_whenRetried_thenItIsUnavailableAndTheOrderStillAwaitsPayment() {
        NewOrder order = placed();
        payments.down(true);

        assertThat(retryAt(PLACED_AT.plusSeconds(60)))
            .isInstanceOf(RetryOrderPayment.Result.ProviderUnavailable.class);
        assertThat(orders.statusOf(order.id())).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAPaidOrder_whenRetried_thenItIsNotPayableAndNothingIsOpened() {
        NewOrder order = placed();
        orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, PLACED_AT.plusSeconds(30));

        assertThat(retryAt(PLACED_AT.plusSeconds(60))).isInstanceOf(RetryOrderPayment.Result.NotPayable.class);
        assertThat(payments.opened()).isEmpty();
    }

    @Test
    void givenAnOrderWhoseReservationIsOver_whenRetried_thenItIsNotPayable() {
        NewOrder order = placed();

        assertThat(retryAt(order.reservationExpiresAt())).isInstanceOf(RetryOrderPayment.Result.NotPayable.class);
        assertThat(payments.opened()).isEmpty();
    }

    @Test
    void givenATokenOfNoOrder_whenRetried_thenThereIsNoOrderAndNothingIsOpened() {
        assertThat(retryAt(PLACED_AT)).isInstanceOf(RetryOrderPayment.Result.NotFound.class);
        assertThat(payments.opened()).isEmpty();
    }

    private NewOrder placed() {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2)),
            OrderAmounts.of(Money.of(4_000_000), Money.zero(), Money.of(450_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        return order;
    }
}
