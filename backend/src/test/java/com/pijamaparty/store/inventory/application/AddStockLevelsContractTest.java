package com.pijamaparty.store.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link AddStockLevels}. */
public abstract class AddStockLevelsContractTest<T extends AddStockLevels> extends ContractTest<T> {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    /** On hand and last update of the variant's stock level; empty without one. */
    protected abstract Optional<Level> levelOf(UUID variantId);

    /** Gives a stock level some stock, as an adjustment of UC-07 would. */
    protected abstract void givenOnHand(UUID variantId, int quantity);

    @Test
    void givenNewVariants_whenTheirLevelsAreAdded_thenEachHasNothingOnHand() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        subject.addEmpty(List.of(first, second, first), NOW);

        assertThat(levelOf(first)).contains(new Level(0, NOW));
        assertThat(levelOf(second)).contains(new Level(0, NOW));
    }

    @Test
    void givenAVariantWithStock_whenItsLevelIsAddedAgain_thenItStaysAsItWas() {
        UUID variant = UUID.randomUUID();
        subject.addEmpty(List.of(variant), NOW);
        givenOnHand(variant, 4);

        subject.addEmpty(List.of(variant), NOW.plusSeconds(60));

        assertThat(levelOf(variant)).contains(new Level(4, NOW));
    }

    public record Level(int onHand, Instant updatedAt) {}
}
