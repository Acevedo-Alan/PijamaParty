package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.Map;

/**
 * Outbound read port: the description of variants as a cart line shows them (UC-08), sold or not, within the caller's
 * transaction.
 */
public interface VariantDescriptionLookup {

    /** The description of each existing variant asked for; unknown variants are left out. */
    Map<VariantId, VariantDescription> describe(Collection<VariantId> variants);
}
