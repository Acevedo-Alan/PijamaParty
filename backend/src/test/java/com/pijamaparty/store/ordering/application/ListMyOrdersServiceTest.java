package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListMyOrdersServiceTest {

    private static final Instant PLACED_AT = Instant.parse("2026-10-13T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final ListMyOrdersService service = new ListMyOrdersService(orders);
    private final UserId ana = UserId.of(UUID.randomUUID());

    @Test
    void givenACustomerWithTwoOrders_whenItOpensMyOrders_thenItSeesBothNewestFirst() {
        NewOrder older = placed(Optional.of(ana), PLACED_AT);
        NewOrder newer = placed(Optional.of(ana), PLACED_AT.plusSeconds(86_400));

        var result = service.execute(new ListMyOrders.Query(customer(ana), 0));

        assertThat(result).isInstanceOfSatisfying(ListMyOrders.Result.Listed.class, listed -> {
            assertThat(listed.orders()).extracting(CustomerOrderSummary::id).containsExactly(newer.id(), older.id());
            assertThat(listed.hasMore()).isFalse();
        });
    }

    @Test
    void givenOrdersOfOtherPeople_whenACustomerOpensMyOrders_thenItSeesOnlyItsOwn() {
        NewOrder anas = placed(Optional.of(ana), PLACED_AT);
        placed(Optional.of(UserId.of(UUID.randomUUID())), PLACED_AT);
        placed(Optional.empty(), PLACED_AT);

        assertThat(service.execute(new ListMyOrders.Query(customer(ana), 0)))
            .isInstanceOfSatisfying(ListMyOrders.Result.Listed.class,
                listed -> assertThat(listed.orders()).extracting(CustomerOrderSummary::id).containsExactly(anas.id()));
    }

    @Test
    void givenMoreOrdersThanAPage_whenListed_thenAPageIsAnsweredAndMoreFollow() {
        List<NewOrder> placed = IntStream.range(0, ListMyOrders.PAGE_SIZE + 1)
            .mapToObj(n -> placed(Optional.of(ana), PLACED_AT.plusSeconds(n)))
            .toList();

        var first = service.execute(new ListMyOrders.Query(customer(ana), 0));
        var second = service.execute(new ListMyOrders.Query(customer(ana), 1));

        assertThat(first).isInstanceOfSatisfying(ListMyOrders.Result.Listed.class, listed -> {
            assertThat(listed.orders()).hasSize(ListMyOrders.PAGE_SIZE);
            assertThat(listed.hasMore()).isTrue();
        });
        assertThat(second).isInstanceOfSatisfying(ListMyOrders.Result.Listed.class, listed -> {
            assertThat(listed.orders()).extracting(CustomerOrderSummary::id).containsExactly(placed.getFirst().id());
            assertThat(listed.hasMore()).isFalse();
        });
    }

    @Test
    void givenAGuest_whenItAsksForMyOrders_thenItIsNotSignedIn() {
        placed(Optional.empty(), PLACED_AT);

        assertThat(service.execute(new ListMyOrders.Query(Actor.anonymous(Optional.empty(), Optional.empty()), 0)))
            .isInstanceOf(ListMyOrders.Result.NotSignedIn.class);
    }

    @Test
    void givenTheAdministrator_whenItAsksForMyOrders_thenItIsNotSignedInAsACustomer() {
        assertThat(service.execute(new ListMyOrders.Query(Actor.admin(ana), 0)))
            .isInstanceOf(ListMyOrders.Result.NotSignedIn.class);
    }

    private static Actor customer(UserId user) {
        return Actor.customer(user, Optional.empty(), Optional.empty());
    }

    private NewOrder placed(Optional<UserId> customer, Instant at) {
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), orders.next(),
            new TrackingToken("token-" + UUID.randomUUID()).hash(), customer,
            new BuyerDetails("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE,
            List.of(new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 1)),
            OrderAmounts.of(Money.of(2_000_000), Money.zero(), Money.of(300_000)), Optional.empty(),
            PaymentProvider.MERCADOPAGO, "2026-10", at, StockHold.expiryOf(at));
        orders.insert(order);
        return order;
    }
}
