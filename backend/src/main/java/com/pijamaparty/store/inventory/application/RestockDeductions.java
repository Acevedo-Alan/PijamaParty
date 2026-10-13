package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockMovement;
import java.util.List;

/** Outbound write port: puts the deducted units of a cancelled order back (UC-18), within the caller's transaction. */
public interface RestockDeductions {

    /**
     * Records each restock, in the given order, and adds its units to the variant's on hand. A deduction already
     * restocked is skipped, so restocking twice changes nothing (RNF-20.4).
     */
    void restock(List<StockMovement> restocks);
}
