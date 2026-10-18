package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound read port (UC-24), backed by {@code ordering.api}: whether a customer bought a product, given its variants.
 * Read as the caller, in its transaction.
 */
public interface PurchasedProducts {

    /**
     * Whether {@code customer} has an order in {@code PAID}, {@code PREPARING}, {@code SHIPPED} or {@code DELIVERED}
     * with any of {@code variantIds}; false for none.
     */
    boolean boughtAnyOf(UserId customer, Set<UUID> variantIds);
}
