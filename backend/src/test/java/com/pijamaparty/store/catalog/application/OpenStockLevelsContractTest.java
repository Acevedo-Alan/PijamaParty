package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link OpenStockLevels}: the stock a new variant gets, read back from where inventory keeps it. */
public abstract class OpenStockLevelsContractTest<T extends OpenStockLevels> extends ContractTest<T> {

    /** What inventory has on hand for the variant; empty without a stock level. */
    protected abstract Optional<Integer> onHandOf(VariantId variant);

    /** Gives an opened variant some stock, as an adjustment of UC-07 would. */
    protected abstract void givenOnHand(VariantId variant, int quantity);

    @Test
    void givenNewVariants_whenOpened_thenEachHasAStockLevelWithNothingOnHand() {
        VariantId first = VariantId.of(UUID.randomUUID());
        VariantId second = VariantId.of(UUID.randomUUID());

        subject.open(List.of(first, second));

        assertThat(onHandOf(first)).contains(0);
        assertThat(onHandOf(second)).contains(0);
        assertThat(onHandOf(VariantId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenAVariantWithStock_whenOpenedAgain_thenItKeepsItsStock() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        subject.open(List.of(variant));
        givenOnHand(variant, 7);

        subject.open(List.of(variant, variant));
        subject.open(List.of());

        assertThat(onHandOf(variant)).contains(7);
    }
}
