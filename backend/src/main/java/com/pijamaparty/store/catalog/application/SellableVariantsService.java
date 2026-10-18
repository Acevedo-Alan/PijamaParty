package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.SellableVariant;
import com.pijamaparty.store.catalog.api.SellableVariants;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-08, UC-23: what the cart may hold, at which price, and what it belongs to. */
@Service
class SellableVariantsService implements SellableVariants {

    private final SellableVariantPrices prices;

    SellableVariantsService(SellableVariantPrices prices) {
        this.prices = prices;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SellableVariant> sellable(Collection<UUID> variantIds) {
        Map<UUID, SellableVariant> sellable = new HashMap<>();
        if (variantIds.isEmpty()) {
            return sellable;
        }
        prices.pricesOf(variantIds.stream().map(VariantId::of).toList())
            .forEach((variant, priced) -> sellable.put(variant.value(),
                new SellableVariant(priced.price(), priced.productId().value(), priced.categoryId().value())));
        return sellable;
    }
}
