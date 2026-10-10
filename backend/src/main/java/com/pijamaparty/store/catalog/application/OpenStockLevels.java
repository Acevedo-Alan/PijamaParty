package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.List;

/** Outbound port: gives new variants their stock level (0 on hand) in the caller's transaction (UC-06, UC-07). */
@FunctionalInterface
public interface OpenStockLevels {

    void open(List<VariantId> variants);
}
