package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.application.AddStockLevelsContractTest.Level;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link AddStockLevels}. */
public class InMemoryAddStockLevels implements AddStockLevels {

    private final Map<UUID, Level> levels = new ConcurrentHashMap<>();

    @Override
    public void addEmpty(Collection<UUID> variantIds, Instant at) {
        variantIds.forEach(id -> levels.putIfAbsent(id, new Level(0, at)));
    }

    public Optional<Level> levelOf(UUID variantId) {
        return Optional.ofNullable(levels.get(variantId));
    }

    /** Test arrangement: stock that an adjustment (UC-07) would add. */
    public void setOnHand(UUID variantId, int quantity) {
        levels.computeIfPresent(variantId, (id, level) -> new Level(quantity, level.updatedAt()));
    }

    public int size() {
        return levels.size();
    }
}
