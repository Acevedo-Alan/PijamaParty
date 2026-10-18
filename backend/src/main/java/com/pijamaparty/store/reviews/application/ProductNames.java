package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Collection;
import java.util.Map;

/** Outbound read port (UC-28), backed by {@code catalog.api}: the names of products, in the caller's transaction. */
public interface ProductNames {

    /** The name of each product asked for, published or not; unknown products are left out. */
    Map<ProductId, String> namesOf(Collection<ProductId> products);
}
