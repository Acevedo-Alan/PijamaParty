package com.pijamaparty.store.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The variants the shop sells now, their prices and what they belong to (UC-08, UC-23): of a published product of an
 * active category, active and priced. Read only; stock is the inventory's ({@code inventory.api.StockAvailability}).
 */
public interface SellableVariants {

    /** Each sellable variant asked for; the others are left out. Joins the caller's transaction. */
    Map<UUID, SellableVariant> sellable(Collection<UUID> variantIds);
}
