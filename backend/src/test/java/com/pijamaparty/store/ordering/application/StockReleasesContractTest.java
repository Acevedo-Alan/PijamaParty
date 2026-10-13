package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link StockReleases} (UC-16). */
public abstract class StockReleasesContractTest<T extends StockReleases> extends ContractTest<T> {

    /** A new variant with {@code onHand} units, of which the order reserved {@code reserved}. */
    protected abstract void givenReserved(OrderId order, UUID variantId, int onHand, int reserved);

    /** On hand − reserved. */
    protected abstract int availableOf(UUID variantId);

    @Test
    void givenAnOrderThatReservedStock_whenReleasedTwice_thenItIsAvailableAgainOnce() {
        OrderId order = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenReserved(order, variant, 3, 2);

        subject.release(order);
        subject.release(order);

        assertThat(availableOf(variant)).isEqualTo(3);
    }

    @Test
    void givenTwoOrders_whenOneIsReleased_thenTheOtherKeepsItsReservation() {
        OrderId released = OrderId.of(UUID.randomUUID());
        OrderId kept = OrderId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        givenReserved(released, variant, 3, 1);
        givenReserved(kept, other, 2, 2);

        subject.release(released);

        assertThat(availableOf(variant)).isEqualTo(3);
        assertThat(availableOf(other)).isZero();
    }

    @Test
    void givenAnOrderWithoutReservations_whenReleased_thenNothingChanges() {
        UUID variant = UUID.randomUUID();
        givenReserved(OrderId.of(UUID.randomUUID()), variant, 3, 1);

        subject.release(OrderId.of(UUID.randomUUID()));

        assertThat(availableOf(variant)).isEqualTo(2);
    }
}
