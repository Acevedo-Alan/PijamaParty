package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ReserveOrderStock} (UC-16). */
public abstract class ReserveOrderStockContractTest<T extends ReserveOrderStock> extends ContractTest<T> {

    /** A new variant with {@code onHand} units and nothing reserved. */
    protected abstract void givenStock(UUID variantId, int onHand);

    /** On hand − reserved; 0 for a variant without stock. */
    protected abstract int availableOf(UUID variantId);

    @Test
    void givenEnoughStockOfEveryLine_whenReserved_thenEachVariantHasItsUnitsLessAvailable() {
        UUID pijama = UUID.randomUUID();
        UUID camison = UUID.randomUUID();
        givenStock(pijama, 5);
        givenStock(camison, 1);

        assertThat(subject.reserve(newOrder(), List.of(line(pijama, 2), line(camison, 1)))).isEmpty();

        assertThat(availableOf(pijama)).isEqualTo(3);
        assertThat(availableOf(camison)).isZero();
    }

    @Test
    void givenOneShortVariant_whenReserved_thenItIsNamedWithWhatIsAvailableAndNothingIsReserved() {
        UUID pijama = UUID.randomUUID();
        UUID camison = UUID.randomUUID();
        givenStock(pijama, 5);
        givenStock(camison, 1);

        assertThat(subject.reserve(newOrder(), List.of(line(pijama, 2), line(camison, 2))))
            .contains(new ReserveOrderStock.Shortage(camison, 1));

        assertThat(availableOf(pijama)).isEqualTo(5);
        assertThat(availableOf(camison)).isEqualTo(1);
    }

    @Test
    void givenTheLastUnit_whenTwoOrdersReserveIt_thenOnlyTheFirstGetsIt() {
        UUID pijama = UUID.randomUUID();
        givenStock(pijama, 1);

        assertThat(subject.reserve(newOrder(), List.of(line(pijama, 1)))).isEmpty();
        assertThat(subject.reserve(newOrder(), List.of(line(pijama, 1))))
            .contains(new ReserveOrderStock.Shortage(pijama, 0));
    }

    @Test
    void givenAVariantWithoutStock_whenReserved_thenNothingIsAvailable() {
        UUID unknown = UUID.randomUUID();

        assertThat(subject.reserve(newOrder(), List.of(line(unknown, 1))))
            .contains(new ReserveOrderStock.Shortage(unknown, 0));
    }

    @Test
    void givenAnOrderThatAlreadyHoldsItsReservation_whenReservedAgain_thenNothingMoreIsReserved() {
        UUID pijama = UUID.randomUUID();
        givenStock(pijama, 5);
        OrderId order = newOrder();

        assertThat(subject.reserve(order, List.of(line(pijama, 2)))).isEmpty();
        assertThat(subject.reserve(order, List.of(line(pijama, 2)))).isEmpty();

        assertThat(availableOf(pijama)).isEqualTo(3);
    }

    protected static OrderLine line(UUID variantId, int quantity) {
        return new OrderLine(variantId, "PP-1", "Pijama", "M", "Rosa", Money.of(2_000_000), quantity);
    }

    private static OrderId newOrder() {
        return OrderId.of(UUID.randomUUID());
    }
}
