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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayOrderWithCardServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-31T15:00:00Z");
    private static final PayOrderWithCard.Card CARD =
        new PayOrderWithCard.Card("card-token-1", "visa", Optional.empty(), 1, "DNI", "12345678");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryChargeCards cards = new InMemoryChargeCards();
    private final TrackingToken token = new TrackingToken("token-" + UUID.randomUUID());

    private PayOrderWithCard.Result payAt(Instant now) {
        return new PayOrderWithCardService(orders, orders, cards, Clock.fixed(now, ZoneOffset.UTC))
            .execute(new PayOrderWithCard.Command(token, UUID.randomUUID(), CARD));
    }

    @Test
    void givenAMercadoPagoOrderAwaitingPayment_whenPaidByCard_thenItsTotalAndEmailAreCharged() {
        NewOrder order = placed(PaymentProvider.MERCADOPAGO);

        var result = payAt(PLACED_AT.plusSeconds(60));

        assertThat(result).isEqualTo(new PayOrderWithCard.Result.Approved());
        assertThat(cards.charged()).singleElement().satisfies(charged -> {
            assertThat(charged.id()).isEqualTo(order.id());
            assertThat(charged.total()).isEqualTo(Money.of(4_450_000));
            assertThat(charged.buyerEmail()).isEqualTo("ana@example.com");
        });
    }

    @Test
    void givenARejectedCard_whenPaid_thenTheRejectionIsAnswered() {
        placed(PaymentProvider.MERCADOPAGO);
        var rejected = new PayOrderWithCard.Result.Rejected(PayOrderWithCard.Rejection.INSUFFICIENT_FUNDS);
        cards.answers(rejected);

        assertThat(payAt(PLACED_AT.plusSeconds(60))).isEqualTo(rejected);
    }

    @Test
    void givenATokenOfNoOrder_whenPaid_thenThereIsNoOrderAndNothingIsCharged() {
        assertThat(payAt(PLACED_AT)).isInstanceOf(PayOrderWithCard.Result.NotFound.class);
        assertThat(cards.charged()).isEmpty();
    }

    @Test
    void givenAPaidOrder_whenPaidAgain_thenItIsNotPayableAndNothingIsCharged() {
        NewOrder order = placed(PaymentProvider.MERCADOPAGO);
        orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, PLACED_AT.plusSeconds(30));

        assertThat(payAt(PLACED_AT.plusSeconds(60))).isInstanceOf(PayOrderWithCard.Result.NotPayable.class);
        assertThat(cards.charged()).isEmpty();
    }

    @Test
    void givenAnOrderWhoseReservationIsOver_whenPaid_thenItIsNotPayableAndNothingIsCharged() {
        NewOrder order = placed(PaymentProvider.MERCADOPAGO);

        assertThat(payAt(order.reservationExpiresAt())).isInstanceOf(PayOrderWithCard.Result.NotPayable.class);
        assertThat(cards.charged()).isEmpty();
    }

    @Test
    void givenAModoOrder_whenPaidByCardOnThePage_thenItIsNotPayableAndNothingIsCharged() {
        placed(PaymentProvider.MODO);

        assertThat(payAt(PLACED_AT.plusSeconds(60))).isInstanceOf(PayOrderWithCard.Result.NotPayable.class);
        assertThat(cards.charged()).isEmpty();
    }

    private NewOrder placed(PaymentProvider provider) {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2)),
            OrderAmounts.of(Money.of(4_000_000), Money.zero(), Money.of(450_000)), Optional.empty(),
            provider, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        return order;
    }
}
