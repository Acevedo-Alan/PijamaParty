package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link StockDeductions} (UC-16): a paid order's reservation becomes a deduction, once. */
public abstract class StockDeductionsContractTest<T extends StockDeductions & StockReleases> extends ContractTest<T> {

    /** A new variant with {@code onHand} units, of which the order reserved {@code reserved}. */
    protected abstract void givenReserved(OrderId order, UUID variantId, int onHand, int reserved);

    /** On hand − reserved. */
    protected abstract int availableOf(UUID variantId);

    /** The units on hand. */
    protected abstract int onHandOf(UUID variantId);

    @Test
    void givenAnOrderThatReservedStock_whenDeductedTwice_thenItsUnitsLeaveTheStockOnce() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenReserved(order, variant, 3, 2);

        subject.deduct(order);
        subject.deduct(order);

        assertThat(onHandOf(variant)).isOne();
        assertThat(availableOf(variant)).isOne();
    }

    @Test
    void givenADeductedOrder_whenItsStockIsReleased_thenNothingComesBack() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenReserved(order, variant, 3, 2);
        subject.deduct(order);

        subject.release(order);

        assertThat(onHandOf(variant)).isOne();
        assertThat(availableOf(variant)).isOne();
    }

    @Test
    void givenAReleasedOrder_whenDeducted_thenNothingLeavesTheStock() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenReserved(order, variant, 3, 2);
        subject.release(order);

        subject.deduct(order);

        assertThat(onHandOf(variant)).isEqualTo(3);
        assertThat(availableOf(variant)).isEqualTo(3);
    }

    @Test
    void givenTwoOrders_whenOneIsDeducted_thenTheOtherKeepsItsReservation() {
        OrderId paid = OrderId.of(UUID.randomUUID());
        OrderId waiting = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        givenReserved(paid, variant, 3, 1);
        givenReserved(waiting, other, 2, 2);

        subject.deduct(paid);

        assertThat(onHandOf(variant)).isEqualTo(2);
        assertThat(onHandOf(other)).isEqualTo(2);
        assertThat(availableOf(other)).isZero();
    }
}
