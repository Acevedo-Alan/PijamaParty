package com.pijamaparty.store.backoffice.adapter.out.catalog;

import com.pijamaparty.store.backoffice.application.CatalogFigures;
import com.pijamaparty.store.catalog.api.LowStockVariants;
import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.catalog.api.PublishedProducts;
import com.pijamaparty.store.catalog.api.VariantDescriptions;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Reads the catalog through its public API, in the caller's transaction. */
@Component
class CatalogModuleFigures implements CatalogFigures {

    private final LowStockVariants lowStockVariants;
    private final PublishedProducts publishedProducts;
    private final VariantDescriptions variantDescriptions;

    CatalogModuleFigures(LowStockVariants lowStockVariants, PublishedProducts publishedProducts,
                         VariantDescriptions variantDescriptions) {
        this.lowStockVariants = lowStockVariants;
        this.publishedProducts = publishedProducts;
        this.variantDescriptions = variantDescriptions;
    }

    @Override
    public List<LowStockVariant> lowStock(int threshold, int limit) {
        return lowStockVariants.list(threshold, limit);
    }

    @Override
    public long lowStockCount(int threshold) {
        return lowStockVariants.count(threshold);
    }

    @Override
    public long publishedProducts() {
        return publishedProducts.count();
    }

    @Override
    public Map<UUID, ProductOf> productsOf(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return variantDescriptions.describe(variantIds).entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> new ProductOf(entry.getValue().productSlug(), entry.getValue().productName())));
    }
}
