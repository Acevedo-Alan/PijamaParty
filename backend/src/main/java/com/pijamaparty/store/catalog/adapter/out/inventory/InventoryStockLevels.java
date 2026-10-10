package com.pijamaparty.store.catalog.adapter.out.inventory;

import com.pijamaparty.store.catalog.application.OpenStockLevels;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.inventory.api.StockLevels;
import java.util.List;
import org.springframework.stereotype.Component;

/** Opens the stock of new variants through the inventory module's public API, in the caller's transaction. */
@Component
class InventoryStockLevels implements OpenStockLevels {

    private final StockLevels stockLevels;

    InventoryStockLevels(StockLevels stockLevels) {
        this.stockLevels = stockLevels;
    }

    @Override
    public void open(List<VariantId> variants) {
        stockLevels.open(variants.stream().map(VariantId::value).toList());
    }
}
