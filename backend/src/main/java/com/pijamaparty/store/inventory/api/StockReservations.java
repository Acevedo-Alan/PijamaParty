package com.pijamaparty.store.inventory.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The stock an order holds while it awaits payment (UC-16): reserved at checkout, deducted for good by an approved
 * payment, released when the order expires. Every method joins the caller's transaction, which must exist, and is
 * idempotent per order (RNF-20.4).
 */
public interface StockReservations {

    /**
     * Reserves every line or none: when a variant is short, nothing of the order stays reserved and the caller's
     * transaction stays usable. A variant without a stock level has 0 available. An order that already holds a
     * reservation is {@link ReservationResult.Reserved} and nothing is reserved again.
     *
     * @param lines one per variant
     */
    ReservationResult reserve(UUID orderId, List<StockRequest> lines);

    /** Every reservation of the order not yet deducted or released takes its units for good. */
    void deduct(UUID orderId);

    /** Every reservation of the order not yet deducted or released gives its units back. */
    void release(UUID orderId);

    /** {@code quantity} units of a variant, from 1 to 1,000,000. */
    record StockRequest(UUID variantId, int quantity) {

        public static final int MAX_QUANTITY = 1_000_000;

        public StockRequest {
            Objects.requireNonNull(variantId, "variantId");
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new IllegalArgumentException("quantity out of range");
            }
        }
    }

    sealed interface ReservationResult {

        record Reserved() implements ReservationResult {}

        /** The first short variant, in variant id order, and what it has available. */
        record InsufficientStock(UUID variantId, int available) implements ReservationResult {}
    }
}
