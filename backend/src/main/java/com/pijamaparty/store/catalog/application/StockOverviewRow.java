package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Objects;

/** One row of the "Stock" screen (UC-07, T-E8-09): an active variant and its units; one without stock has none. */
public record StockOverviewRow(VariantId variantId, ProductId productId, String productName, String size, String color,
                               String sku, boolean published, int onHand, int reserved, int available) {

    public StockOverviewRow {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(sku, "sku");
    }
}
