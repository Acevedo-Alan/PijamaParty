package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.util.List;

/** Outbound port: reserves the stock of an order (UC-16), within the caller's transaction. */
public interface ReserveStock {

    /**
     * Records each reservation and raises the reserved units of its variant, in the given order, only when the
     * variant's available stock (on hand − reserved) covers it. All or nothing: when one is short, none of them stays
     * recorded and the caller's transaction stays usable.
     */
    Outcome reserveAll(List<StockMovement> reservations);

    sealed interface Outcome {

        record Reserved() implements Outcome {}

        /** The first short variant and its available stock; 0 when it has no stock level. */
        record Short(VariantId variantId, int available) implements Outcome {}
    }
}
