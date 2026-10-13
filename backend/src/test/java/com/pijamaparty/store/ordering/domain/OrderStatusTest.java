package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void givenTheStateDiagramOfUc18_whenEveryPairIsChecked_thenOnlyItsTransitionsAreAllowed() {
        Map<OrderStatus, Set<OrderStatus>> allowed = Map.of(
            OrderStatus.AWAITING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.EXPIRED, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));

        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canBecome(to)).as(from + " to " + to)
                    .isEqualTo(allowed.getOrDefault(from, Set.of()).contains(to));
            }
        }
    }

    @Test
    void givenAnAwaitingPaymentOrder_whenMovingItToShipped_thenItIsNotAllowed() {
        assertThat(OrderStatus.AWAITING_PAYMENT.canBecome(OrderStatus.SHIPPED)).isFalse();
    }
}
