package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.CorrectShipmentTrackingNumber.Command;
import com.pijamaparty.store.ordering.application.CorrectShipmentTrackingNumber.Result;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrectShipmentTrackingNumberServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final CorrectShipmentTrackingNumberService service = new CorrectShipmentTrackingNumberService(orders,
        orders, new InMemoryOrderActivity(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrderId order = OrderId.of(UUID.randomUUID());

    @Test
    void givenAShippedOrder_whenItsTrackingNumberIsCorrected_thenTheBuyerSeesTheNewOne() {
        orders.add(order, OrderStatus.PAID, NOW);
        orders.ship(order, "CA000000000AR");

        assertThat(service.execute(new Command(ADMIN, order, " CA123456789AR ")))
            .isEqualTo(new Result.Corrected(new ShipmentTrackingNumber("CA123456789AR")));
        assertThat(orders.shipmentTrackingNumberOf(order)).contains("CA123456789AR");
    }

    @Test
    void givenAnOrderNotShipped_whenItsTrackingNumberIsCorrected_thenNothingChanges() {
        orders.add(order, OrderStatus.DELIVERED, NOW);

        assertThat(service.execute(new Command(ADMIN, order, "CA123456789AR")))
            .isEqualTo(new Result.NotShipped(OrderStatus.DELIVERED));
        assertThat(orders.shipmentTrackingNumberOf(order)).isEmpty();
    }

    @Test
    void givenABlankOrTooLongNumber_whenCorrected_thenItIsInvalid() {
        orders.add(order, OrderStatus.PAID, NOW);
        orders.ship(order, "CA000000000AR");

        assertThat(service.execute(new Command(ADMIN, order, " "))).isEqualTo(new Result.InvalidTrackingNumber());
        assertThat(service.execute(new Command(ADMIN, order, "x".repeat(61))))
            .isEqualTo(new Result.InvalidTrackingNumber());
        assertThat(orders.shipmentTrackingNumberOf(order)).contains("CA000000000AR");
    }

    @Test
    void givenNoSuchOrder_whenCorrected_thenItIsNotFound() {
        assertThat(service.execute(new Command(ADMIN, order, "CA123456789AR"))).isEqualTo(new Result.NotFound());
    }

    @Test
    void givenSomeoneButTheAdministrator_whenCorrecting_thenItIsForbidden() {
        assertThat(service.execute(new Command(Actor.system(), order, "CA123456789AR")))
            .isEqualTo(new Result.Forbidden());
    }
}
