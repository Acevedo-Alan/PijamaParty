package com.pijamaparty.store.inventory.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link VariantSkus}: only the variants added to it have a SKU. */
public class InMemoryVariantSkus implements VariantSkus {

    private final Map<UUID, String> skus = new ConcurrentHashMap<>();

    public void add(UUID variant, String sku) {
        skus.put(variant, sku);
    }

    @Override
    public Optional<String> skuOf(UUID variantId) {
        return Optional.ofNullable(skus.get(variantId));
    }
}
