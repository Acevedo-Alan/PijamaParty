package com.pijamaparty.store.catalog.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The variants the shop sells now and their prices (UC-08): of a published product of an active category, active and
 * priced. Read only; stock is the inventory's ({@code inventory.api.StockAvailability}).
 */
public interface SellableVariants {

    /** The price of each sellable variant asked for; the others are left out. Joins the caller's transaction. */
    Map<UUID, Money> prices(Collection<UUID> variantIds);
}
