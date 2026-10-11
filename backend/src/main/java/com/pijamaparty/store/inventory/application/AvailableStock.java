package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.VariantId;
import java.util.Collection;
import java.util.Map;

/** Outbound read port of the stock that can be sold (UC-03): on hand − reserved. */
public interface AvailableStock {

    /** The available stock of the variants that have a stock level; the others are left out. */
    Map<VariantId, Integer> availableOf(Collection<VariantId> variantIds);
}
