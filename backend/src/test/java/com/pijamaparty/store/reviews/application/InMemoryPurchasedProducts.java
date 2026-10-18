package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link PurchasedProducts}: a customer bought a variant once {@link #bought} says so. */
public class InMemoryPurchasedProducts implements PurchasedProducts {

    private final Map<UserId, Set<UUID>> bought = new ConcurrentHashMap<>();

    public void bought(UserId customer, UUID variant) {
        bought.computeIfAbsent(customer, c -> ConcurrentHashMap.newKeySet()).add(variant);
    }

    /** As when the order of every variant the customer bought is cancelled. */
    public void cancelAll(UserId customer) {
        bought.remove(customer);
    }

    @Override
    public boolean boughtAnyOf(UserId customer, Set<UUID> variantIds) {
        Set<UUID> own = bought.getOrDefault(customer, Set.of());
        return variantIds.stream().anyMatch(own::contains);
    }
}
