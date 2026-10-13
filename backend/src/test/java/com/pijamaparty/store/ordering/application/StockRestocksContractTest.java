package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link StockRestocks} (UC-18). */
public abstract class StockRestocksContractTest<T extends StockRestocks> extends ContractTest<T> {

    /** A new variant with {@code onHand} units, of which the paid order took {@code deducted} for good. */
    protected abstract void givenDeducted(OrderId order, UUID variantId, int onHand, int deducted);

    /** A new variant with {@code onHand} units, of which the order reserved {@code reserved}, then released them. */
    protected abstract void givenReleased(OrderId order, UUID variantId, int onHand, int reserved);

    /** The units on hand. */
    protected abstract int onHandOf(UUID variantId);

    @Test
    void givenAPaidOrder_whenRestockedTwice_thenItsUnitsAreBackOnHandOnce() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenDeducted(order, variant, 3, 2);

        subject.restock(order);
        subject.restock(order);

        assertThat(onHandOf(variant)).isEqualTo(3);
    }

    @Test
    void givenAnOrderWhoseReservationWasReleased_whenRestocked_thenNothingChanges() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenReleased(order, variant, 3, 2);

        subject.restock(order);

        assertThat(onHandOf(variant)).isEqualTo(3);
    }

    @Test
    void givenTwoPaidOrders_whenOneIsRestocked_thenTheOtherKeepsItsDeduction() {
        OrderId restocked = OrderId.of(UUID.randomUUID());
        OrderId kept = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        givenDeducted(restocked, variant, 3, 1);
        givenDeducted(kept, other, 2, 2);

        subject.restock(restocked);

        assertThat(onHandOf(variant)).isEqualTo(3);
        assertThat(onHandOf(other)).isZero();
    }
}
