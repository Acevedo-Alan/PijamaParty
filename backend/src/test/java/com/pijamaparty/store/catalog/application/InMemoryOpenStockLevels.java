package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link OpenStockLevels}: remembers the stock level (0 on hand) of every opened variant, as inventory does. */
public class InMemoryOpenStockLevels implements OpenStockLevels {

    private final Map<VariantId, Integer> onHand = new ConcurrentHashMap<>();

    @Override
    public void open(List<VariantId> variants) {
        variants.forEach(variant -> onHand.putIfAbsent(variant, 0));
    }

    public Optional<Integer> onHandOf(VariantId variant) {
        return Optional.ofNullable(onHand.get(variant));
    }

    public int opened() {
        return onHand.size();
    }

    /** Test arrangement: stock that an adjustment (UC-07) would add. */
    public void setOnHand(VariantId variant, int quantity) {
        onHand.computeIfPresent(variant, (id, previous) -> quantity);
    }
}
