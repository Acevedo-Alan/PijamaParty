package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link OverdueOrders} and {@link OrderTransitions} over the same orders (UC-16, RNF-20.5). */
public abstract class OrderStoreContractTest<T extends OverdueOrders & OrderTransitions> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    /** An order in {@code status} whose stock reservation expires at {@code reservationExpiresAt}. */
    protected abstract void givenOrder(OrderId order, OrderStatus status, Instant reservationExpiresAt);

    /** Empty when there is no such order. */
    protected abstract Optional<OrderStatus> statusOf(OrderId order);

    @Test
    void givenOrdersAwaitingPayment_whenLookingForTheNextDue_thenTheOneExpiredLongestAgoComesFirst() {
        OrderId recent = OrderId.of(UUID.randomUUID());
        OrderId oldest = OrderId.of(UUID.randomUUID());
        givenOrder(recent, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));
        givenOrder(oldest, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(600));
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT, NOW.plusSeconds(1));
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.PAID, NOW.minusSeconds(3600));
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.EXPIRED, NOW.minusSeconds(3600));

        assertThat(subject.lockNextDue(NOW)).contains(oldest);
    }

    @Test
    void givenAReservationExpiringExactlyNow_whenLookingForTheNextDue_thenItIsDue() {
        OrderId order = OrderId.of(UUID.randomUUID());
        givenOrder(order, OrderStatus.AWAITING_PAYMENT, NOW);

        assertThat(subject.lockNextDue(NOW)).contains(order);
        assertThat(subject.lockNextDue(NOW.minusMillis(1))).isEmpty();
    }

    @Test
    void givenNoExpiredReservation_whenLookingForTheNextDue_thenThereIsNone() {
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT, NOW.plusSeconds(1800));

        assertThat(subject.lockNextDue(NOW)).isEmpty();
    }

    @Test
    void givenAnOrderAwaitingPayment_whenExpiredTwice_thenOnlyTheFirstTransitionApplies() {
        OrderId order = OrderId.of(UUID.randomUUID());
        givenOrder(order, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));

        assertThat(subject.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW)).isTrue();
        persistAndForget();
        assertThat(subject.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW)).isFalse();
        persistAndForget();

        assertThat(statusOf(order)).contains(OrderStatus.EXPIRED);
        assertThat(subject.lockNextDue(NOW)).isEmpty();
    }

    @Test
    void givenAnOrderInAnotherStatus_whenTransitionedFromAwaitingPayment_thenNothingChanges() {
        OrderId order = OrderId.of(UUID.randomUUID());
        givenOrder(order, OrderStatus.PAID, NOW.minusSeconds(60));

        assertThat(subject.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW)).isFalse();
        assertThat(subject.transition(OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT,
            OrderStatus.EXPIRED, NOW)).isFalse();
        persistAndForget();

        assertThat(statusOf(order)).contains(OrderStatus.PAID);
    }

    @Test
    void givenATransitionOutsideTheStateDiagram_whenAttempted_thenItIsRefused() {
        OrderId order = OrderId.of(UUID.randomUUID());
        givenOrder(order, OrderStatus.EXPIRED, NOW.minusSeconds(60));

        assertThatThrownBy(() -> subject.transition(order, OrderStatus.EXPIRED, OrderStatus.PAID, NOW))
            .isInstanceOf(RuntimeException.class).hasMessageContaining("cannot go from EXPIRED to PAID");
        assertThat(statusOf(order)).contains(OrderStatus.EXPIRED);
    }
}
