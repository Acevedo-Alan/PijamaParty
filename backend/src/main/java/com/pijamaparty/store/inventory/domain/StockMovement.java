package com.pijamaparty.store.inventory.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One change of the stock of a variant (UC-07). Immutable and append-only: the history is never edited. The quantity
 * is signed (positive adds); the administrator's movements carry the user and the adjustment key.
 */
public record StockMovement(MovementId id, VariantId variantId, MovementType type, int quantity,
                            Optional<MovementNote> note, Optional<UserId> userId,
                            Optional<AdjustmentKey> adjustmentKey, Instant createdAt) {

    public StockMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(adjustmentKey, "adjustmentKey");
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
    }

    /** Whether an adjustment with this movement's key asks for the same change, so it is a retry (UC-07). */
    public boolean repeats(int otherQuantity, Optional<MovementNote> otherNote) {
        return quantity == otherQuantity && note.equals(otherNote);
    }
}
