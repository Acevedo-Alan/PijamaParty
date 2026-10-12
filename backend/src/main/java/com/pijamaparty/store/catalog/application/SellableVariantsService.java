package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.SellableVariants;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-08: what the cart may hold and at which price. */
@Service
class SellableVariantsService implements SellableVariants {

    private final SellableVariantPrices prices;

    SellableVariantsService(SellableVariantPrices prices) {
        this.prices = prices;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Money> prices(Collection<UUID> variantIds) {
        Map<UUID, Money> sellable = new HashMap<>();
        if (variantIds.isEmpty()) {
            return sellable;
        }
        prices.pricesOf(variantIds.stream().map(VariantId::of).toList())
            .forEach((variant, price) -> sellable.put(variant.value(), price));
        return sellable;
    }
}
