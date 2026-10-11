package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Fake of {@link LoadStockLevels}, {@link SaveStockLevels} and {@link StockHistory} over the same levels and
 * movements. It stores rows, not aggregates, like the database: a loaded level that is not saved changes nothing.
 * Locks are not modeled.
 */
public class InMemoryStockStore implements LoadStockLevels, SaveStockLevels, StockHistory {

    /** Newest first; the id breaks ties as PostgreSQL orders uuids (by their bytes, as the canonical text does). */
    private static final Comparator<StockMovement> NEWEST_FIRST = Comparator.comparing(StockMovement::createdAt)
        .thenComparing(movement -> movement.id().value().toString())
        .reversed();

    private final Map<VariantId, Row> levels = new HashMap<>();
    private final List<StockMovement> movements = new ArrayList<>();

    /** Test arrangement: the stock level that opening a new variant creates (UC-06). */
    public synchronized void open(VariantId variantId, Instant at) {
        levels.putIfAbsent(variantId, new Row(0, at));
    }

    @Override
    public synchronized Optional<StockLevel> lockByVariantId(VariantId variantId) {
        return Optional.ofNullable(levels.get(variantId))
            .map(row -> StockLevel.of(variantId, row.onHand(), hasMovements(variantId), row.updatedAt()));
    }

    @Override
    public synchronized Optional<StockMovement> findAdjustment(VariantId variantId, AdjustmentKey key) {
        return movements.stream()
            .filter(movement -> movement.variantId().equals(variantId))
            .filter(movement -> movement.adjustmentKey().equals(Optional.of(key)))
            .findFirst();
    }

    @Override
    public synchronized void save(StockLevel level) {
        for (StockMovement movement : level.newMovements()) {
            Row row = levels.get(level.variantId());
            if (row == null) {
                throw new IllegalStateException("no stock level for variant " + level.variantId());
            }
            int onHand = row.onHand() + movement.quantity();
            if (onHand < 0 || onHand > StockLevel.MAX_ON_HAND) {
                throw new IllegalStateException("stock out of range");
            }
            if (movement.adjustmentKey().isPresent()
                && findAdjustment(movement.variantId(), movement.adjustmentKey().get()).isPresent()) {
                throw new IllegalStateException("adjustment key already used");
            }
            levels.put(level.variantId(), new Row(onHand, movement.createdAt()));
            movements.add(movement);
        }
    }

    @Override
    public synchronized OptionalInt onHand(VariantId variantId) {
        Row row = levels.get(variantId);
        return row == null ? OptionalInt.empty() : OptionalInt.of(row.onHand());
    }

    @Override
    public synchronized List<StockMovement> movements(VariantId variantId, int offset, int limit) {
        return movements.stream()
            .filter(movement -> movement.variantId().equals(variantId))
            .sorted(NEWEST_FIRST)
            .skip(offset)
            .limit(limit)
            .toList();
    }

    private boolean hasMovements(VariantId variantId) {
        return movements.stream().anyMatch(movement -> movement.variantId().equals(variantId));
    }

    private record Row(int onHand, Instant updatedAt) {}
}
