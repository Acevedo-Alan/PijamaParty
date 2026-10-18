package com.pijamaparty.store.cart.adapter.out.catalog;

import com.pijamaparty.store.cart.application.VariantAvailability;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.catalog.api.SellableVariants;
import com.pijamaparty.store.inventory.api.StockAvailability;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Whether the catalog sells variants, at which price and of which product and category, through its public API, and
 * their available stock through the inventory's, both in the caller's transaction.
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
    public Map<VariantId, SellableVariant> sellable(Collection<VariantId> variantIds) {
        Map<VariantId, SellableVariant> sellable = new HashMap<>();
        if (variantIds.isEmpty()) {
            return sellable;
        }
        Map<UUID, com.pijamaparty.store.catalog.api.SellableVariant> sold =
            sellableVariants.sellable(variantIds.stream().map(VariantId::value).toList());
        if (sold.isEmpty()) {
            return sellable;
        }
        Map<UUID, Integer> available = stockAvailability.available(List.copyOf(sold.keySet()));
        sold.forEach((id, variant) -> sellable.put(VariantId.of(id), new SellableVariant(variant.price(),
            available.getOrDefault(id, 0), variant.productId(), variant.categoryId())));
        return sellable;
    }
}
