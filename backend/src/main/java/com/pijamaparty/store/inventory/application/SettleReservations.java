package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockMovement;
import java.util.List;

/** Outbound port: deducts or releases reservations (UC-16), within the caller's transaction. */
public interface SettleReservations {

    /**
     * Records each deduction or release, in the given order, and applies it: a deduction lowers the variant's on hand
     * and reserved units, a release only the reserved ones. A reservation already deducted or released is skipped, so
     * settling twice changes nothing (RNF-20.4).
     */
    void settle(List<StockMovement> settlements);
}
