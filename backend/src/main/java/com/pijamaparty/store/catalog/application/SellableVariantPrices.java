package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Outbound read port: the prices of variants that can be sold now, with their product and category, within the
 * caller's transaction. Sellable as in the public catalog (UC-03): a published product of an active category, the
 * variant active and priced.
 */
public interface SellableVariantPrices {

    /** Each sellable variant asked for; the others are left out. */
    Map<VariantId, Priced> pricesOf(Collection<VariantId> variants);

    /** A sellable variant's price, and the product and category it belongs to (UC-23). */
    record Priced(Money price, ProductId productId, CategoryId categoryId) {

        public Priced {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(categoryId, "categoryId");
        }
    }
}
