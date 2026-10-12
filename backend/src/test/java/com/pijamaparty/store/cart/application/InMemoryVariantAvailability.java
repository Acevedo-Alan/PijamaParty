package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link VariantAvailability}: a variant is sold once {@link #sell} sets its price and stock. */
public class InMemoryVariantAvailability implements VariantAvailability {

    private final Map<VariantId, SellableVariant> sold = new ConcurrentHashMap<>();

    public void sell(VariantId variantId, Money price, int available) {
        sold.put(variantId, new SellableVariant(price, available));
    }

    /** The catalog stops selling it (hidden, deactivated or unpriced). */
    public void stopSelling(VariantId variantId) {
        sold.remove(variantId);
    }

    @Override
    public Optional<SellableVariant> of(VariantId variantId) {
        return Optional.ofNullable(sold.get(variantId));
    }
}
