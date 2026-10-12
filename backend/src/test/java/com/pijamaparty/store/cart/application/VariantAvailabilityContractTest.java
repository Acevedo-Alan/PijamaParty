package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.application.VariantAvailability.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link VariantAvailability} (UC-08). */
public abstract class VariantAvailabilityContractTest<T extends VariantAvailability> extends ContractTest<T> {

    /** A variant the catalog sells at {@code price}, with {@code available} units, or no stock level when null. */
    protected abstract void givenSold(VariantId variantId, Money price, Integer available);

    /** A variant of a hidden product: it exists and has {@code available} units, but the catalog does not sell it. */
    protected abstract void givenNotSold(VariantId variantId, int available);

    @Test
    void givenASoldVariantWithStock_whenAskingForIt_thenItHasItsPriceAndAvailableStock() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenSold(variant, Money.of(1_500_000), 3);

        assertThat(subject.of(variant)).contains(new SellableVariant(Money.of(1_500_000), 3));
    }

    @Test
    void givenASoldVariantWithoutAStockLevel_whenAskingForIt_thenNothingIsAvailable() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenSold(variant, Money.of(1_500_000), null);

        assertThat(subject.of(variant)).contains(new SellableVariant(Money.of(1_500_000), 0));
    }

    @Test
    void givenAVariantTheCatalogDoesNotSell_whenAskingForIt_thenItIsNotSellable() {
        VariantId variant = VariantId.of(UUID.randomUUID());
        givenNotSold(variant, 3);

        assertThat(subject.of(variant)).isEmpty();
    }

    @Test
    void givenAnUnknownVariant_whenAskingForIt_thenItIsNotSellable() {
        assertThat(subject.of(VariantId.of(UUID.randomUUID()))).isEmpty();
    }
}
