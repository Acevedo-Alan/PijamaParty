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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetTrackedOrderServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-13T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryLinePhotos photos = new InMemoryLinePhotos();
    private final TrackingToken token = new TrackingToken("token-" + UUID.randomUUID());

    private GetTrackedOrder.Result findAt(Instant now) {
        return new GetTrackedOrderService(orders, photos, Clock.fixed(now, ZoneOffset.UTC))
            .execute(new GetTrackedOrder.Query(token));
    }

    @Test
    void givenABuyerBackWithoutPaying_whenOpeningItsTrackingLink_thenTheOrderIsPendingWithTheOptionToRetry() {
        NewOrder order = placed();

        var result = findAt(PLACED_AT.plusSeconds(60));

        assertThat(result).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class, found -> {
            assertThat(found.order().id()).isEqualTo(order.id());
            assertThat(found.order().status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(found.payable()).isTrue();
        });
    }

    @Test
    void givenAnOrder_whenOpeningItsTrackingLink_thenOnlyTheBuyersFirstNameAsTypedIsGiven() {
        placed();

        assertThat(findAt(PLACED_AT)).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> assertThat(found.order().buyerFirstName()).isEqualTo("María José"));
    }

    @Test
    void givenAnOrderWhoseReservationIsOver_whenOpeningItsTrackingLink_thenThePaymentCannotBeRetried() {
        NewOrder order = placed();

        assertThat(findAt(order.reservationExpiresAt())).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> assertThat(found.payable()).isFalse());
    }

    @Test
    void givenAPaidOrder_whenOpeningItsTrackingLink_thenThePaymentCannotBeRetried() {
        NewOrder order = placed();
        orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, PLACED_AT.plusSeconds(60));

        assertThat(findAt(PLACED_AT.plusSeconds(120))).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> {
                assertThat(found.order().status()).isEqualTo(OrderStatus.PAID);
                assertThat(found.payable()).isFalse();
            });
    }

    @Test
    void givenAShippedOrder_whenOpeningItsTrackingLink_thenItShowsTheShipmentTrackingNumber() {
        NewOrder order = placed();
        orders.ship(order.id(), "CA123456789AR");

        assertThat(findAt(PLACED_AT.plusSeconds(60))).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> assertThat(found.order().shipmentTrackingNumber()).contains("CA123456789AR"));
    }

    @Test
    void givenAnItemWhoseProductHasAPhoto_whenOpeningItsTrackingLink_thenThePhotoComesByVariant() {
        NewOrder order = placed();
        UUID variant = order.lines().getFirst().variantId();
        var photo = new LinePhotos.Photo("data:image/webp;base64,UklGRg==",
            List.of(new LinePhotos.PhotoFile(400, "luna-rosa-400.webp")));
        photos.add(variant, photo);

        assertThat(findAt(PLACED_AT)).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> assertThat(found.photos()).isEqualTo(Map.of(variant, photo)));
    }

    @Test
    void givenAnItemWhoseProductHasNoPhoto_whenOpeningItsTrackingLink_thenNoPhotoComes() {
        placed();

        assertThat(findAt(PLACED_AT)).isInstanceOfSatisfying(GetTrackedOrder.Result.Found.class,
            found -> assertThat(found.photos()).isEmpty());
    }

    @Test
    void givenATokenOfNoOrder_whenOpened_thenThereIsNoOrder() {
        assertThat(findAt(PLACED_AT)).isInstanceOf(GetTrackedOrder.Result.NotFound.class);
    }

    private NewOrder placed() {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(), token.hash(), Optional.empty(),
            new BuyerDetails("María José", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2)),
            OrderAmounts.of(Money.of(4_000_000), Money.zero(), Money.of(450_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        return order;
    }
}
