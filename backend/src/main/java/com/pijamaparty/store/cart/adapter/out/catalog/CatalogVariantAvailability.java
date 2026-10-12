package com.pijamaparty.store.cart.adapter.out.catalog;

import com.pijamaparty.store.cart.application.VariantAvailability;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.catalog.api.SellableVariants;
import com.pijamaparty.store.inventory.api.StockAvailability;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Whether the catalog sells a variant and at which price, through its public API, and its available stock through
 * the inventory's, both in the caller's transaction.
 */
@Component
class CatalogVariantAvailability implements VariantAvailability {

    private final SellableVariants sellableVariants;
    private final StockAvailability stockAvailability;

    CatalogVariantAvailability(SellableVariants sellableVariants, StockAvailability stockAvailability) {
        this.sellableVariants = sellableVariants;
        this.stockAvailability = stockAvailability;
    }

    @Override
    public Optional<SellableVariant> of(VariantId variantId) {
        Money price = sellableVariants.prices(List.of(variantId.value())).get(variantId.value());
        if (price == null) {
            return Optional.empty();
        }
        int available = stockAvailability.available(List.of(variantId.value())).getOrDefault(variantId.value(), 0);
        return Optional.of(new SellableVariant(price, available));
    }
}
