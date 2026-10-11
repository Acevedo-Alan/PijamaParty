package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-07: the administrator adds stock to a variant or subtracts it, with an optional note. Every change appends a
 * movement; a retry with the same adjustment key changes nothing.
 */
public interface AdjustStock {

    Result execute(Command command);

    /** {@code quantity} is signed (positive adds); {@code note} as the administrator sent it. */
    record Command(Actor actor, VariantId variantId, AdjustmentKey key, int quantity, Optional<String> note) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(note, "note");
        }
    }

    sealed interface Result {

        record Adjusted(int newOnHand) implements Result {}

        /** A retry: the key was used before with the same quantity and note. The stock is the current one. */
        record AlreadyAdjusted(int onHand) implements Result {}

        record WouldGoNegative(int currentOnHand) implements Result {}

        /** The stock would be above {@code StockLevel.MAX_ON_HAND}. */
        record WouldExceedMaximum(int currentOnHand) implements Result {}

        /** Not from −10,000 to 10,000, or 0. */
        record InvalidQuantity() implements Result {}

        /** Longer than 200 characters, more than one line, or with invisible characters. */
        record InvalidNote() implements Result {}

        /** The key was used before with another quantity or note. */
        record KeyReused() implements Result {}

        record UnknownVariant() implements Result {}

        record Forbidden() implements Result {}
    }
}
