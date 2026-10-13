package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ConfirmableOrders.ConfirmableOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ConfirmableOrders} (UC-15): the order an approved payment is for. */
public abstract class ConfirmableOrdersContractTest<T extends ConfirmableOrders> extends ContractTest<T> {

    protected static final Instant EXPIRES = Instant.parse("2026-10-12T15:30:00Z");

    /** An order in {@code status} that applied the coupon, if any; answers its number. */
    protected abstract long givenOrder(OrderId order, OrderStatus status, Optional<UUID> couponId);

    @Test
    void givenAnOrderAwaitingPaymentWithACoupon_whenLocked_thenItsNumberStatusAndCouponAreRead() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID coupon = UUID.randomUUID();
        long number = givenOrder(order, OrderStatus.AWAITING_PAYMENT, Optional.of(coupon));

        assertThat(subject.lock(order)).contains(
            new ConfirmableOrder(order, number, OrderStatus.AWAITING_PAYMENT, Optional.of(coupon)));
    }

    @Test
    void givenAnExpiredOrderWithoutACoupon_whenLocked_thenItIsReadAsExpiredWithoutACoupon() {
        OrderId order = OrderId.of(UUID.randomUUID());
        long number = givenOrder(order, OrderStatus.EXPIRED, Optional.empty());

        assertThat(subject.lock(order)).contains(
            new ConfirmableOrder(order, number, OrderStatus.EXPIRED, Optional.empty()));
    }

    @Test
    void givenNoSuchOrder_whenLocked_thenNothingIsFound() {
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT, Optional.empty());

        assertThat(subject.lock(OrderId.of(UUID.randomUUID()))).isEmpty();
    }
}
