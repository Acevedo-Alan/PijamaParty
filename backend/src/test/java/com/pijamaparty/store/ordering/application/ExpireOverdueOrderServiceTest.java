package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ExpireOverdueOrder.Result;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpireOverdueOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryStockReleases stock = new InMemoryStockReleases();
    private final ExpireOverdueOrderService service = new ExpireOverdueOrderService(orders, orders, stock,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAnExpiredReservation_whenTheCleanupRuns_thenTheStockIsAvailableAgainAndTheOrderIsExpired() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));
        stock.reserved(order, variant, 1, 1);

        Result result = service.execute();

        assertThat(result).isEqualTo(new Result.Expired(order));
        assertThat(orders.statusOf(order)).contains(OrderStatus.EXPIRED);
        assertThat(stock.availableOf(variant)).isEqualTo(1);
    }

    @Test
    void givenSeveralExpiredOrders_whenTheCleanupRunsUntilNoneIsDue_thenEachExpiresOnceOldestFirst() {
        OrderId oldest = OrderId.of(UUID.randomUUID());
        OrderId recent = OrderId.of(UUID.randomUUID());
        orders.add(recent, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));
        orders.add(oldest, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(600));

        assertThat(service.execute()).isEqualTo(new Result.Expired(oldest));
        assertThat(service.execute()).isEqualTo(new Result.Expired(recent));
        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
    }

    @Test
    void givenAReservationStillRunning_whenTheCleanupRuns_thenTheOrderKeepsWaitingForItsPayment() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW.plusSeconds(1));
        stock.reserved(order, variant, 1, 1);

        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
        assertThat(orders.statusOf(order)).contains(OrderStatus.AWAITING_PAYMENT);
        assertThat(stock.wasReleased(order)).isFalse();
    }

    @Test
    void givenAPaidOrderPastItsReservation_whenTheCleanupRuns_thenItKeepsItsStock() {
        OrderId order = OrderId.of(UUID.randomUUID());
        orders.add(order, OrderStatus.PAID, NOW.minusSeconds(3600));
        stock.reserved(order, UUID.randomUUID(), 1, 1);

        assertThat(service.execute()).isEqualTo(new Result.NoneDue());
        assertThat(orders.statusOf(order)).contains(OrderStatus.PAID);
        assertThat(stock.wasReleased(order)).isFalse();
    }
}
