package com.pijamaparty.store.catalog.domain;

/** Hands out the id and the SKU of each variant a {@link Product} creates; the application backs it with its ports. */
public interface VariantIdentities {

    VariantId nextId();

    Sku nextSku();
}
