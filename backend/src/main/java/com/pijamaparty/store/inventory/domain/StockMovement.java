package com.pijamaparty.store.inventory.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One change of the stock of a variant (UC-07). Immutable and append-only: the history is never edited. The quantity
 * is signed (positive adds); the administrator's movements carry the user and the adjustment key, and the order
 * movements their order (UC-16): a reservation and a deduction are negative, a release positive.
 */
public record StockMovement(MovementId id, VariantId variantId, MovementType type, int quantity,
                            Optional<MovementNote> note, Optional<UserId> userId,
                            Optional<AdjustmentKey> adjustmentKey, Optional<OrderRef> order, Instant createdAt) {

    public StockMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(adjustmentKey, "adjustmentKey");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity == 0) {
            throw new IllegalArgumentException("a movement changes the stock");
        }
        if (type.isAdministrative() && userId.isEmpty()) {
            throw new IllegalArgumentException(type + " requires the administrator");
        }
        if (type.isAdministrative() != adjustmentKey.isPresent()) {
            throw new IllegalArgumentException(type + (type.isAdministrative() ? " requires" : " cannot have")
                + " an adjustment key");
        }
        if (type.isOrderMovement() != order.isPresent()) {
            throw new IllegalArgumentException(type + (type.isOrderMovement() ? " requires" : " cannot have")
                + " an order");
        }
        if (type == MovementType.RELEASE ? quantity < 0 : type.isOrderMovement() && quantity > 0) {
            throw new IllegalArgumentException(type + " has the wrong sign");
        }
    }

    /** {@code units} reserved by the order, a positive number recorded as a negative movement. */
    public static StockMovement reservation(MovementId id, VariantId variantId, OrderRef order, int units,
                                            Instant at) {
        return orderMovement(id, variantId, MovementType.RESERVATION, Math.negateExact(units), order, at);
    }

    /** Takes this reservation's units for good. */
    public StockMovement deduction(MovementId id, Instant at) {
        requireReservation();
        return orderMovement(id, variantId, MovementType.DEDUCTION, quantity, order.orElseThrow(), at);
    }

    /** Gives this reservation's units back. */
    public StockMovement release(MovementId id, Instant at) {
        requireReservation();
        return orderMovement(id, variantId, MovementType.RELEASE, Math.negateExact(quantity), order.orElseThrow(), at);
    }

    /** The units this order movement holds, takes or gives back, as a positive number. */
    public int units() {
        return Math.abs(quantity);
    }

    /** Whether an adjustment with this movement's key asks for the same change, so it is a retry (UC-07). */
    public boolean repeats(int otherQuantity, Optional<MovementNote> otherNote) {
        return quantity == otherQuantity && note.equals(otherNote);
    }

    private void requireReservation() {
        if (type != MovementType.RESERVATION) {
            throw new IllegalStateException("only a reservation is deducted or released");
        }
    }

    private static StockMovement orderMovement(MovementId id, VariantId variantId, MovementType type, int quantity,
                                               OrderRef order, Instant at) {
        return new StockMovement(id, variantId, type, quantity, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(order), at);
    }
}
