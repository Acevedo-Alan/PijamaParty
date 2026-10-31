package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.LowStockVariants;
import com.pijamaparty.store.catalog.api.PublishedProducts;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-33: the low stock and the published products for the back-office summary. */
@Service
class StockWatchService implements LowStockVariants, PublishedProducts {

    private final StockWatch stockWatch;

    StockWatchService(StockWatch stockWatch) {
        this.stockWatch = stockWatch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockVariant> list(int threshold, int limit) {
        return stockWatch.lowStock(threshold, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(int threshold) {
        return stockWatch.lowStockCount(threshold);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return stockWatch.publishedProducts();
    }
}
