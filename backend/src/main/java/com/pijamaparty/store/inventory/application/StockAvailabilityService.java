package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.api.StockAvailability;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-03: what the catalog shows as available; a variant without a stock level has nothing to sell. */
@Service
class StockAvailabilityService implements StockAvailability {

    private final AvailableStock availableStock;

    StockAvailabilityService(AvailableStock availableStock) {
        this.availableStock = availableStock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> available(Collection<UUID> variantIds) {
        Map<UUID, Integer> available = new HashMap<>();
        if (variantIds.isEmpty()) {
            return available;
        }
        Map<VariantId, Integer> stocked = availableStock.availableOf(variantIds.stream().map(VariantId::of).toList());
        variantIds.forEach(id -> available.put(id, stocked.getOrDefault(VariantId.of(id), 0)));
        return available;
    }
}
