package com.pijamaparty.store.catalog.api;

import java.util.Optional;
import java.util.UUID;

/**
 * UC-24 (T-E5-07): the product a visitor sees at a slug, so the product page can read its reviews. Read only, in the
 * caller's transaction.
 */
public interface ListedProductIds {

    /**
     * The id of the product at {@code slug} as the visitor sent it; empty when no product has it or visitors do not see
     * it (hidden, of an inactive category or unsellable), as the product page of UC-03.
     */
    Optional<UUID> listedProductId(String slug);
}
