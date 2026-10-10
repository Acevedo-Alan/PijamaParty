package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Sku;

/** Outbound port: a new SKU for a variant (UC-06). Unique with high probability; the database has the last word. */
@FunctionalInterface
public interface SkuGenerator {

    Sku newSku();
}
