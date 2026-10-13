package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ChangeOrderStatus.Command;
import com.pijamaparty.store.ordering.application.ChangeOrderStatus.Result;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeOrderStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryStockReleases stock = new InMemoryStockReleases();
    private final ChangeOrderStatusService service = new ChangeOrderStatusService(orders, orders, stock, stock,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrderId order = OrderId.of(UUID.randomUUID());
    private final UUID variant = UUID.randomUUID();

    @Test
    void givenAPaidOrder_whenPrepared_thenItIsPreparing() {
        orders.add(order, OrderStatus.PAID, NOW);

        assertThat(service.execute(command(OrderStatus.PAID, OrderStatus.PREPARING)))
            .isEqualTo(new Result.Changed(OrderStatus.PREPARING));
        assertThat(orders.statusOf(order)).contains(OrderStatus.PREPARING);
    }

    @Test
    void givenAnOrderAwaitingPayment_whenMovedToShipped_thenItIsRejected() {
        orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW);

        assertThat(service.execute(new Command(ADMIN, order, OrderStatus.AWAITING_PAYMENT, OrderStatus.SHIPPED,
            Optional.of("CA123456789AR"), Optional.empty()))).isEqualTo(new Result.InvalidTransition());
        assertThat(orders.statusOf(order)).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAPreparingOrder_whenShippedWithATrackingNumber_thenItIsShippedWithTheNumberTrimmed() {
        orders.add(order, OrderStatus.PREPARING, NOW);

        Result result = service.execute(new Command(ADMIN, order, OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of(" CA123456789AR "), Optional.empty()));

        assertThat(result).isEqualTo(new Result.Changed(OrderStatus.SHIPPED));
        assertThat(orders.statusOf(order)).contains(OrderStatus.SHIPPED);
        assertThat(orders.shipmentTrackingNumberOf(order)).contains("CA123456789AR");
    }

    @Test
    void givenAPreparingOrder_whenShippedWithoutOrWithABlankTrackingNumber_thenNothingChanges() {
        orders.add(order, OrderStatus.PREPARING, NOW);

        assertThat(service.execute(command(OrderStatus.PREPARING, OrderStatus.SHIPPED)))
            .isEqualTo(new Result.MissingTrackingNumber());
        assertThat(service.execute(new Command(ADMIN, order, OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of("  "), Optional.empty()))).isEqualTo(new Result.InvalidTrackingNumber());
        assertThat(orders.statusOf(order)).contains(OrderStatus.PREPARING);
    }

    @Test
    void givenAShippedOrder_whenDelivered_thenItsDeliveryIsRecorded() {
        orders.add(order, OrderStatus.SHIPPED, NOW);

        assertThat(service.execute(command(OrderStatus.SHIPPED, OrderStatus.DELIVERED)))
            .isEqualTo(new Result.Changed(OrderStatus.DELIVERED));
        assertThat(orders.deliveredAtOf(order)).contains(NOW);
    }

    @Test
    void givenAnOrderAwaitingPayment_whenCancelled_thenItsReservationIsReleased() {
        orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW.plusSeconds(600));
        stock.reserved(order, variant, 3, 2);

        assertThat(service.execute(command(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED)))
            .isEqualTo(new Result.Changed(OrderStatus.CANCELLED));
        assertThat(stock.availableOf(variant)).isEqualTo(3);
    }

    @Test
    void givenAPaidOrder_whenCancelledPuttingTheStockBack_thenEveryLinesUnitsAreAvailableAgain() {
        paid();

        assertThat(service.execute(new Command(ADMIN, order, OrderStatus.PAID, OrderStatus.CANCELLED,
            Optional.empty(), Optional.of(true)))).isEqualTo(new Result.Changed(OrderStatus.CANCELLED));
        assertThat(stock.wasRestocked(order)).isTrue();
        assertThat(stock.availableOf(variant)).isEqualTo(3);
    }

    @Test
    void givenAPaidOrder_whenCancelledWithoutPuttingTheStockBack_thenTheStockDoesNotChange() {
        paid();

        assertThat(service.execute(new Command(ADMIN, order, OrderStatus.PAID, OrderStatus.CANCELLED,
            Optional.empty(), Optional.of(false)))).isEqualTo(new Result.Changed(OrderStatus.CANCELLED));
        assertThat(stock.wasRestocked(order)).isFalse();
        assertThat(stock.availableOf(variant)).isOne();
    }

    @Test
    void givenAPaidOrder_whenCancelledWithoutDecidingAboutTheStock_thenNothingChanges() {
        paid();

        assertThat(service.execute(command(OrderStatus.PAID, OrderStatus.CANCELLED)))
            .isEqualTo(new Result.RestockDecisionRequired());
        assertThat(orders.statusOf(order)).contains(OrderStatus.PAID);
    }

    @Test
    void givenAChangeWithAFieldItDoesNotTake_whenExecuted_thenTheFieldIsNamed() {
        orders.add(order, OrderStatus.PAID, NOW);

        assertThat(service.execute(new Command(ADMIN, order, OrderStatus.PAID, OrderStatus.PREPARING,
            Optional.empty(), Optional.of(true)))).isEqualTo(new Result.UnexpectedField("restock"));
    }

    @Test
    void givenAnOrderThatChangedAfterTheAdministratorOpenedIt_whenChangedFromTheStatusSeen_thenNothingChanges() {
        orders.add(order, OrderStatus.PAID, NOW);
        stock.reserved(order, variant, 3, 2);

        assertThat(service.execute(command(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED)))
            .isEqualTo(new Result.StatusChanged(OrderStatus.PAID));
        assertThat(orders.statusOf(order)).contains(OrderStatus.PAID);
        assertThat(stock.wasReleased(order)).isFalse();
    }

    @Test
    void givenNoSuchOrder_whenChanged_thenItIsNotFound() {
        assertThat(service.execute(command(OrderStatus.PAID, OrderStatus.PREPARING)))
            .isEqualTo(new Result.NotFound());
    }

    @Test
    void givenSomeoneButTheAdministrator_whenChangingAnOrder_thenItIsForbidden() {
        orders.add(order, OrderStatus.PAID, NOW);

        assertThat(service.execute(new Command(Actor.system(), order, OrderStatus.PAID, OrderStatus.PREPARING,
            Optional.empty(), Optional.empty()))).isEqualTo(new Result.Forbidden());
        assertThat(orders.statusOf(order)).contains(OrderStatus.PAID);
    }

    private Command command(OrderStatus from, OrderStatus to) {
        return new Command(ADMIN, order, from, to, Optional.empty(), Optional.empty());
    }

    /** 2 of 3 units taken for good by the order. */
    private void paid() {
        orders.add(order, OrderStatus.PAID, NOW);
        stock.reserved(order, variant, 3, 2);
        stock.deduct(order);
    }
}
