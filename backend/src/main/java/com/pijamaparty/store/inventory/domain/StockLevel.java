package com.pijamaparty.store.inventory.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The stock on hand of one variant (UC-07). Owns its invariants: stock never goes negative, below what orders awaiting
 * payment reserved (UC-16) or above {@link #MAX_ON_HAND}, and every change appends a {@link StockMovement}. The
 * administrator's first movement is the initial load, every later one an adjustment.
 */
public final class StockLevel {

    public static final int MAX_ON_HAND = 1_000_000;
    /** The largest quantity one adjustment adds or subtracts. */
    public static final int MAX_ADJUSTMENT = 10_000;

    private final VariantId variantId;
    private int onHand;
    private final int reserved;
    private boolean hasMovements;
    private Instant updatedAt;
    private final List<StockMovement> newMovements;

    private StockLevel(VariantId variantId, int onHand, int reserved, boolean hasMovements, Instant updatedAt) {
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (onHand < 0 || onHand > MAX_ON_HAND) {
            throw new IllegalArgumentException("stock out of range");
        }
        if (reserved < 0 || reserved > onHand) {
            throw new IllegalArgumentException("reserved stock out of range");
        }
        this.onHand = onHand;
        this.reserved = reserved;
        this.hasMovements = hasMovements;
        this.newMovements = new ArrayList<>();
    }

    /**
     * A stored level; {@code reserved} is what orders awaiting payment hold, and {@code hasMovements} tells whether its
     * history has any movement.
     */
    public static StockLevel of(VariantId variantId, int onHand, int reserved, boolean hasMovements,
                                Instant updatedAt) {
        return new StockLevel(variantId, onHand, reserved, hasMovements, updatedAt);
    }

    /** From −{@link #MAX_ADJUSTMENT} to {@link #MAX_ADJUSTMENT}, not 0. */
    public static boolean isValidAdjustment(int quantity) {
        return quantity != 0 && quantity >= -MAX_ADJUSTMENT && quantity <= MAX_ADJUSTMENT;
    }

    /**
     * Adds (positive) or subtracts (negative) {@code quantity} on behalf of the administrator. The stock stays as it
     * was when the result would be negative, below the reserved units or above the maximum.
     */
    public AdjustOutcome adjust(MovementId id, int quantity, Optional<MovementNote> note, UserId by, AdjustmentKey key,
                                Instant now) {
        if (!isValidAdjustment(quantity)) {
            throw new IllegalArgumentException("not a valid adjustment");
        }
        int result = onHand + quantity;
        if (result < 0) {
            return new AdjustOutcome.WouldGoNegative(onHand);
        }
        if (result < reserved) {
            return new AdjustOutcome.WouldGoBelowReserved(onHand, reserved);
        }
        if (result > MAX_ON_HAND) {
            return new AdjustOutcome.WouldExceedMaximum(onHand);
        }
        MovementType type = hasMovements ? MovementType.ADJUSTMENT : MovementType.INITIAL_LOAD;
        var movement = new StockMovement(id, variantId, type, quantity, note, Optional.of(by), Optional.of(key),
            Optional.empty(), now);
        onHand = result;
        hasMovements = true;
        updatedAt = now;
        newMovements.add(movement);
        return new AdjustOutcome.Adjusted(movement, onHand);
    }

    public sealed interface AdjustOutcome {

        record Adjusted(StockMovement movement, int onHand) implements AdjustOutcome {}

        record WouldGoNegative(int currentOnHand) implements AdjustOutcome {}

        /** UC-16: orders awaiting payment hold {@code reserved} units, which an adjustment cannot take away. */
        record WouldGoBelowReserved(int currentOnHand, int reserved) implements AdjustOutcome {}

        record WouldExceedMaximum(int currentOnHand) implements AdjustOutcome {}
    }

    public VariantId variantId() {
        return variantId;
    }

    public int onHand() {
        return onHand;
    }

    public int reserved() {
        return reserved;
    }

    public boolean hasMovements() {
        return hasMovements;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** The movements appended since the level was loaded, oldest first: what saving it records. */
    public List<StockMovement> newMovements() {
        return List.copyOf(newMovements);
    }
}
