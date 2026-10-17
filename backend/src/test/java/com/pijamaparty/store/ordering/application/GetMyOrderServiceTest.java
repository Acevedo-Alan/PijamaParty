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
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetMyOrderServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-13T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final GetMyOrderService service = new GetMyOrderService(orders);
    private final UserId ana = UserId.of(UUID.randomUUID());

    @Test
    void givenAShippedOrderOfTheCustomer_whenItOpensIt_thenItSeesItemsAddressStatusAndTrackingNumber() {
        NewOrder order = placed(Optional.of(ana));
        orders.ship(order.id(), "CA123456789AR");

        var result = service.execute(new GetMyOrder.Query(customer(ana), order.id()));

        assertThat(result).isInstanceOfSatisfying(GetMyOrder.Result.Found.class, found -> {
            assertThat(found.order().lines()).isEqualTo(order.lines());
            assertThat(found.order().address()).isEqualTo(order.address());
            assertThat(found.order().status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(found.order().shipmentTrackingNumber()).contains("CA123456789AR");
        });
    }

    @Test
    void givenACustomer_whenItTriesToOpenAnotherPersonsOrder_thenItIsNotFound() {
        NewOrder brunos = placed(Optional.of(UserId.of(UUID.randomUUID())));

        assertThat(service.execute(new GetMyOrder.Query(customer(ana), brunos.id())))
            .isInstanceOf(GetMyOrder.Result.NotFound.class);
    }

    @Test
    void givenACustomer_whenItTriesToOpenAGuestOrder_thenItIsNotFound() {
        NewOrder guests = placed(Optional.empty());

        assertThat(service.execute(new GetMyOrder.Query(customer(ana), guests.id())))
            .isInstanceOf(GetMyOrder.Result.NotFound.class);
    }

    @Test
    void givenAGuest_whenItAsksForAnOrderOfMyOrders_thenItIsNotSignedIn() {
        NewOrder order = placed(Optional.of(ana));

        assertThat(service.execute(new GetMyOrder.Query(Actor.anonymous(Optional.empty(), Optional.empty()),
            order.id()))).isInstanceOf(GetMyOrder.Result.NotSignedIn.class);
    }

    private static Actor customer(UserId user) {
        return Actor.customer(user, Optional.empty(), Optional.empty());
    }

    private NewOrder placed(Optional<UserId> customer) {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(),
            new TrackingToken("token-" + UUID.randomUUID()).hash(), customer,
            new BuyerDetails("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", PLACED_AT, StockHold.expiryOf(PLACED_AT));
        orders.insert(order);
        return order;
    }
}
