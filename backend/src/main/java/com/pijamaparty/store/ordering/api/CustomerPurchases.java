package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Collection;
import java.util.UUID;

/**
 * UC-24 (T-E5-06): whether a customer bought a product, for who may review it. Read as the caller, in its transaction
 * or a short read-only one: Row Level Security shows a customer only its own orders (V14).
 */
public interface CustomerPurchases {

    /**
     * Whether {@code customer} has an order in {@code PAID}, {@code PREPARING}, {@code SHIPPED} or {@code DELIVERED}
     * with any of {@code variantIds}; false for none.
     */
    boolean boughtAnyOf(UserId customer, Collection<UUID> variantIds);
}
