package com.pijamaparty.store.catalog.adapter.out.inventory;

import com.pijamaparty.store.catalog.application.VariantAvailabilityLookup;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.inventory.api.StockAvailability;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Reads the available stock through the inventory module's public API, in the caller's transaction. */
@Component
class InventoryVariantAvailability implements VariantAvailabilityLookup {

    private final StockAvailability stockAvailability;

    InventoryVariantAvailability(StockAvailability stockAvailability) {
        this.stockAvailability = stockAvailability;
    }

    @Override
    public Map<VariantId, Integer> availableOf(Collection<VariantId> variants) {
        return stockAvailability.available(variants.stream().map(VariantId::value).toList()).entrySet().stream()
            .collect(Collectors.toMap(entry -> VariantId.of(entry.getKey()), Map.Entry::getValue));
    }
}
