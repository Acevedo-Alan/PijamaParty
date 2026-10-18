package com.pijamaparty.store.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The names of products, published or not, for records that only keep a product's id (UC-28). Read only. */
public interface ProductNames {

    /** The name of each product asked for; unknown products are left out. Joins the caller's transaction. */
    Map<UUID, String> namesOf(Collection<UUID> productIds);
}
