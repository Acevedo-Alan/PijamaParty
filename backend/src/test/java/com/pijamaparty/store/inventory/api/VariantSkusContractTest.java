package com.pijamaparty.store.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link VariantSkus}, which the catalog implements for the inventory module (UC-37). */
public abstract class VariantSkusContractTest<T extends VariantSkus> extends ContractTest<T> {

    /** A variant the catalog has, and its SKU. */
    public record Given(UUID variant, String sku) {
    }

    protected abstract Given givenVariant();

    @Test
    void givenAVariant_whenAskedForItsSku_thenItIsTheOneTheCatalogGaveIt() {
        Given given = givenVariant();

        assertThat(subject.skuOf(given.variant())).contains(given.sku());
    }

    @Test
    void givenAnUnknownVariant_whenAskedForItsSku_thenThereIsNone() {
        assertThat(subject.skuOf(UUID.randomUUID())).isEmpty();
    }
}
