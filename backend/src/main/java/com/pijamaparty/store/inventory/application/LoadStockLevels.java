package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import java.util.Optional;

/** Outbound port: what an adjustment reads (UC-07), within the caller's transaction. */
public interface LoadStockLevels {

    /**
     * The stock level of the variant, locked until the transaction ends ({@code SELECT ... FOR UPDATE}), so two
     * adjustments of one variant run one after the other; empty when the variant has none.
     */
    Optional<StockLevel> lockByVariantId(VariantId variantId);

    /** The movement recorded with this adjustment key for the variant, if any. */
    Optional<StockMovement> findAdjustment(VariantId variantId, AdjustmentKey key);
}
