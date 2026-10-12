package com.pijamaparty.store.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What a cart line shows of its variant (UC-08): its product's name and slug, its size and color, and a photo. Read
 * only; whether the shop sells it, and at which price, is {@link SellableVariants}.
 */
public interface VariantDescriptions {

    /**
     * The description of each variant asked for, sold or not, so a line the shop no longer sells still shows what it
     * was; unknown variants are left out. Joins the caller's transaction.
     */
    Map<UUID, VariantDescription> describe(Collection<UUID> variantIds);
}
