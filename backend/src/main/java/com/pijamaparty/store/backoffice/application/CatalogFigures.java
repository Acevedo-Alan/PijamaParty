package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Outbound read port (UC-33): what the summary reads of the catalog, from its public API. */
public interface CatalogFigures {

    /** Active variants of published products at or under {@code threshold} available units, sold-out first. */
    List<LowStockVariant> lowStock(int threshold, int limit);

    long lowStockCount(int threshold);

    long publishedProducts();

    /** The product of each variant asked for, to group the best sellers; unknown variants are left out. */
    Map<UUID, ProductOf> productsOf(Collection<UUID> variantIds);

    record ProductOf(String slug, String name) {

        public ProductOf {
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(name, "name");
        }
    }
}
