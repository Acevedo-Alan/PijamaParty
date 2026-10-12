package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link ItemDescriptions}: a variant is described once {@link #add} gives its description. */
public class InMemoryItemDescriptions implements ItemDescriptions {

    private final Map<VariantId, ItemDescription> described = new ConcurrentHashMap<>();

    public void add(VariantId variantId, ItemDescription description) {
        described.put(variantId, description);
    }

    @Override
    public Map<VariantId, ItemDescription> describe(Collection<VariantId> variantIds) {
        Map<VariantId, ItemDescription> found = new HashMap<>();
        variantIds.forEach(id -> {
            ItemDescription description = described.get(id);
            if (description != null) {
                found.put(id, description);
            }
        });
        return found;
    }
}
