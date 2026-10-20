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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenOrderPaymentServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-12T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryOpenPayments payments = new InMemoryOpenPayments();

    private OpenOrderPaymentService serviceAt(Instant now) {
        return new OpenOrderPaymentService(orders, payments, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void givenAPlacedOrder_whenItsPaymentIsOpened_thenThePaymentUrlIsAnsweredForItsTotalAndBuyer() {
        NewOrder order = placed();

        var result = serviceAt(PLACED_AT.plusSeconds(1)).execute(new OpenOrderPayment.Command(order.id()));

        assertThat(result).isEqualTo(new OpenOrderPayment.Result.Opened(
            URI.create("https://pay.example.com/" + order.number())));
        assertThat(payments.opened()).singleElement().satisfies(toPay -> {
            assertThat(toPay.number()).isEqualTo(order.number());
            assertThat(toPay.total()).isEqualTo(Money.of(4_450_000));
            assertThat(toPay.buyerName()).isEqualTo("Ana Pérez");
            assertThat(toPay.buyerEmail()).isEqualTo("ana@example.com");
            assertThat(toPay.reservationExpiresAt()).isEqualTo(order.reservationExpiresAt());
        });
    }

    @Test
    void givenTheProviderDown_whenThePaymentIsOpened_thenItIsUnavailableAndTheOrderStillAwaitsPayment() {
        NewOrder order = placed();
        payments.down(true);

        var result = serviceAt(PLACED_AT.plusSeconds(1)).execute(new OpenOrderPayment.Command(order.id()));

        assertThat(result).isInstanceOf(OpenOrderPayment.Result.ProviderUnavailable.class);
        assertThat(orders.statusOf(order.id())).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenNoSuchOrder_whenItsPaymentIsOpened_thenItIsNotPayable() {
        var result = serviceAt(PLACED_AT).execute(new OpenOrderPayment.Command(OrderId.of(UUID.randomUUID())));

        assertThat(result).isInstanceOf(OpenOrderPayment.Result.NotPayable.class);
        assertThat(payments.opened()).isEmpty();
    }

    @Test
    void givenAnOrderNoLongerAwaitingPayment_whenItsPaymentIsOpened_thenItIsNotPayable() {
        NewOrder order = placed();
        orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, PLACED_AT.plusSeconds(1));

        var result = serviceAt(PLACED_AT.plusSeconds(2)).execute(new OpenOrderPayment.Command(order.id()));

        assertThat(result).isInstanceOf(OpenOrderPayment.Result.NotPayable.class);
        assertThat(payments.opened()).isEmpty();
    }

    @Test
    void givenAnOrderWhoseReservationIsOver_whenItsPaymentIsOpened_thenItIsNotPayable() {
        NewOrder order = placed();
        Instant expiry = order.reservationExpiresAt();

        assertThat(serviceAt(expiry).execute(new OpenOrderPayment.Command(order.id())))
            .isInstanceOf(OpenOrderPayment.Result.NotPayable.class);
        assertThat(serviceAt(expiry.minus(Duration.ofSeconds(1))).execute(new OpenOrderPayment.Command(order.id())))
            .isInstanceOf(OpenOrderPayment.Result.Opened.class);
    }

    private NewOrder placed() {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(),
            new TrackingToken("token-" + UUID.randomUUID()).hash(), Optional.empty(), new BuyerDetails("Ana", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2)),
            OrderAmounts.of(Money.of(4_000_000), Money.zero(), Money.of(450_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        return order;
    }
}
