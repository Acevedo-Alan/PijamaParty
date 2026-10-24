package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.inventory.api.VariantSkusContractTest;
import java.util.List;

/** The catalog implements the inventory module's {@code VariantSkus} over the variants it stores. */
class VariantSkusServiceContractTest extends VariantSkusContractTest<VariantSkusService> {

    private final ProductFixture fixture = new ProductFixture();

    @Override
    protected VariantSkusService createSubject() {
        return new VariantSkusService(new InMemoryVariantDescriptionLookup(fixture.products));
    }

    @Override
    protected Given givenVariant() {
        ProductView.VariantView variant = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"))
            .variants().getFirst();
        return new Given(variant.id().value(), variant.sku());
    }
}
