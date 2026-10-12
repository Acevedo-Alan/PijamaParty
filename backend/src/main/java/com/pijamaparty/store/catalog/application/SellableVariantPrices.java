package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.Map;

/**
 * Outbound read port: the prices of variants that can be sold now, within the caller's transaction. Sellable as in
 * the public catalog (UC-03): a published product of an active category, the variant active and priced.
 */
public interface SellableVariantPrices {

    /** The price of each sellable variant asked for; the others are left out. */
    Map<VariantId, Money> pricesOf(Collection<VariantId> variants);
}
