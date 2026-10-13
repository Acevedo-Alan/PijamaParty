package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: reserves the stock of an order's lines (UC-16), within the caller's transaction. */
@FunctionalInterface
public interface ReserveOrderStock {

    /**
     * All or nothing: empty when every line is reserved, otherwise the first short variant, and then nothing of the
     * order stays reserved. An order that already holds its reservation is not reserved again.
     *
     * @param lines one per variant
     */
    Optional<Shortage> reserve(OrderId order, List<OrderLine> lines);

    /** A variant without enough stock, and the units it has available. */
    record Shortage(UUID variantId, int available) {

        public Shortage {
            Objects.requireNonNull(variantId, "variantId");
            if (available < 0) {
                throw new IllegalArgumentException("available stock cannot be negative");
            }
        }
    }
}
