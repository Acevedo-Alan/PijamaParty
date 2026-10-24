package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.inventory.api.VariantSkus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-37: the SKU that names a variant in the activity log of a stock adjustment, for the inventory module. */
@Service
class VariantSkusService implements VariantSkus {

    private final VariantDescriptionLookup lookup;

    VariantSkusService(VariantDescriptionLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> skuOf(UUID variantId) {
        VariantId variant = VariantId.of(variantId);
        return Optional.ofNullable(lookup.describe(List.of(variant)).get(variant)).map(VariantDescription::sku);
    }
}
