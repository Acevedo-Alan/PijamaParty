package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.Map;

/** Outbound read port: the inventory's available stock of variants (UC-03), within the caller's transaction. */
public interface VariantAvailabilityLookup {

    /** An entry for every variant asked for: 0 for one without stock. */
    Map<VariantId, Integer> availableOf(Collection<VariantId> variants);
}
