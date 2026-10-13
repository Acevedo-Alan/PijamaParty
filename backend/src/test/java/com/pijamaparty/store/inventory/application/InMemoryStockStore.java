package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.MovementType;
import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Fake of {@link LoadStockLevels}, {@link SaveStockLevels}, {@link StockHistory}, {@link AvailableStock},
 * {@link ReserveStock}, {@link OrderReservations}, {@link SettleReservations}, {@link OrderDeductions} and
 * {@link RestockDeductions} over the same levels and movements. It stores rows, not aggregates, like the database: a
 * loaded level that is not saved changes nothing. Locks are not modeled.
 */
public class InMemoryStockStore implements LoadStockLevels, SaveStockLevels, StockHistory, AvailableStock,
    ReserveStock, OrderReservations, SettleReservations, OrderDeductions, RestockDeductions {

    /** Newest first; the id breaks ties as PostgreSQL orders uuids (by their bytes, as the canonical text does). */
    private static final Comparator<StockMovement> NEWEST_FIRST = Comparator.comparing(StockMovement::createdAt)
        .thenComparing(movement -> movement.id().value().toString())
        .reversed();

    private final Map<VariantId, Row> levels = new HashMap<>();
    private final List<StockMovement> movements = new ArrayList<>();

    /** Test arrangement: the stock level that opening a new variant creates (UC-06). */
    public synchronized void open(VariantId variantId, Instant at) {
        levels.putIfAbsent(variantId, new Row(0, 0, at));
    }

    /** Test inspection: the units orders awaiting payment hold; empty without a stock level. */
    public synchronized OptionalInt reserved(VariantId variantId) {
        Row row = levels.get(variantId);
        return row == null ? OptionalInt.empty() : OptionalInt.of(row.reserved());
    }

    @Override
    public synchronized Optional<StockLevel> lockByVariantId(VariantId variantId) {
        return Optional.ofNullable(levels.get(variantId))
            .map(row -> StockLevel.of(variantId, row.onHand(), row.reserved(), hasMovements(variantId),
                row.updatedAt()));
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
            if (onHand < row.reserved() || onHand > StockLevel.MAX_ON_HAND) {
                throw new IllegalStateException("stock out of range");
            }
            if (movement.adjustmentKey().isPresent()
                && findAdjustment(movement.variantId(), movement.adjustmentKey().get()).isPresent()) {
                throw new IllegalStateException("adjustment key already used");
            }
            levels.put(level.variantId(), new Row(onHand, row.reserved(), movement.createdAt()));
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

    @Override
    public synchronized Map<VariantId, Integer> availableOf(Collection<VariantId> variantIds) {
        Map<VariantId, Integer> available = new HashMap<>();
        variantIds.stream().filter(levels::containsKey)
            .forEach(variantId -> available.put(variantId, levels.get(variantId).available()));
        return available;
    }

    /** All or nothing, as the savepoint of the real adapter: the rows change only when every reservation fits. */
    @Override
    public synchronized Outcome reserveAll(List<StockMovement> reservations) {
        for (StockMovement reservation : reservations) {
            if (reservation.type() != MovementType.RESERVATION) {
                throw new IllegalArgumentException("not a reservation: " + reservation.type());
            }
            Row row = levels.get(reservation.variantId());
            if (row == null || row.available() < reservation.units()) {
                return new Outcome.Short(reservation.variantId(), row == null ? 0 : row.available());
            }
        }
        for (StockMovement reservation : reservations) {
            Row row = levels.get(reservation.variantId());
            levels.put(reservation.variantId(),
                new Row(row.onHand(), row.reserved() + reservation.units(), reservation.createdAt()));
            movements.add(reservation);
        }
        return new Outcome.Reserved();
    }

    @Override
    public synchronized boolean hasAny(OrderRef order) {
        return movements.stream().anyMatch(movement -> movement.order().equals(Optional.of(order))
            && movement.type() == MovementType.RESERVATION);
    }

    @Override
    public synchronized List<StockMovement> outstanding(OrderRef order) {
        return movements.stream()
            .filter(movement -> movement.order().equals(Optional.of(order)))
            .filter(movement -> movement.type() == MovementType.RESERVATION)
            .filter(reservation -> !isSettled(order, reservation.variantId()))
            .sorted(Comparator.comparing(movement -> movement.variantId().value().toString()))
            .toList();
    }

    @Override
    public synchronized void settle(List<StockMovement> settlements) {
        for (StockMovement settlement : settlements) {
            if (settlement.type() != MovementType.DEDUCTION && settlement.type() != MovementType.RELEASE) {
                throw new IllegalArgumentException("not a deduction or a release: " + settlement.type());
            }
            OrderRef order = settlement.order().orElseThrow();
            if (isSettled(order, settlement.variantId())) {
                continue;
            }
            Row row = levels.get(settlement.variantId());
            int taken = settlement.type() == MovementType.DEDUCTION ? settlement.units() : 0;
            int reserved = row.reserved() - settlement.units();
            if (reserved < 0) {
                throw new IllegalStateException("reserved stock out of range");
            }
            levels.put(settlement.variantId(), new Row(row.onHand() - taken, reserved, settlement.createdAt()));
            movements.add(settlement);
        }
    }

    @Override
    public synchronized List<StockMovement> notRestocked(OrderRef order) {
        return movements.stream()
            .filter(movement -> movement.order().equals(Optional.of(order)))
            .filter(movement -> movement.type() == MovementType.DEDUCTION)
            .filter(deduction -> !has(order, deduction.variantId(), MovementType.RESTOCK))
            .sorted(Comparator.comparing(movement -> movement.variantId().value().toString()))
            .toList();
    }

    @Override
    public synchronized void restock(List<StockMovement> restocks) {
        for (StockMovement restock : restocks) {
            if (restock.type() != MovementType.RESTOCK) {
                throw new IllegalArgumentException("not a restock: " + restock.type());
            }
            OrderRef order = restock.order().orElseThrow();
            if (has(order, restock.variantId(), MovementType.RESTOCK)) {
                continue;
            }
            Row row = levels.get(restock.variantId());
            if (row.onHand() + restock.quantity() > StockLevel.MAX_ON_HAND) {
                throw new IllegalStateException("on hand stock out of range");
            }
            levels.put(restock.variantId(),
                new Row(row.onHand() + restock.quantity(), row.reserved(), restock.createdAt()));
            movements.add(restock);
        }
    }

    private boolean has(OrderRef order, VariantId variantId, MovementType type) {
        return movements.stream().anyMatch(movement -> movement.order().equals(Optional.of(order))
            && movement.variantId().equals(variantId) && movement.type() == type);
    }

    private boolean isSettled(OrderRef order, VariantId variantId) {
        return movements.stream().anyMatch(movement -> movement.order().equals(Optional.of(order))
            && movement.variantId().equals(variantId)
            && (movement.type() == MovementType.DEDUCTION || movement.type() == MovementType.RELEASE));
    }

    private boolean hasMovements(VariantId variantId) {
        return movements.stream().anyMatch(movement -> movement.variantId().equals(variantId));
    }

    private record Row(int onHand, int reserved, Instant updatedAt) {

        int available() {
            return onHand - reserved;
        }
    }
}
