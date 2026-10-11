package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.util.List;
import java.util.OptionalInt;

/** Outbound read port of the stock history of a variant (UC-07). */
public interface StockHistory {

    /** The stock on hand of the variant; empty when it has no stock level. */
    OptionalInt onHand(VariantId variantId);

    /** Its movements, newest first, skipping {@code offset} and returning at most {@code limit}. */
    List<StockMovement> movements(VariantId variantId, int offset, int limit);
}
