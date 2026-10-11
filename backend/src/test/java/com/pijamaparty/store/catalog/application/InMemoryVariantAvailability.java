package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link VariantAvailabilityLookup}: the inventory's available stock, set by {@link #setAvailable}. */
public class InMemoryVariantAvailability implements VariantAvailabilityLookup {

    private final Map<VariantId, Integer> available = new ConcurrentHashMap<>();

    /** Test arrangement: a variant never set has none. */
    public void setAvailable(VariantId variant, int units) {
        available.put(variant, units);
    }

    @Override
    public Map<VariantId, Integer> availableOf(Collection<VariantId> variants) {
        Map<VariantId, Integer> answer = new HashMap<>();
        variants.forEach(variant -> answer.put(variant, available.getOrDefault(variant, 0)));
        return answer;
    }
}
