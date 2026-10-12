package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.api.VariantDescriptions;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-08: what each cart line shows of its variant. */
@Service
class VariantDescriptionsService implements VariantDescriptions {

    private final VariantDescriptionLookup lookup;

    VariantDescriptionsService(VariantDescriptionLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VariantDescription> describe(Collection<UUID> variantIds) {
        Map<UUID, VariantDescription> described = new HashMap<>();
        if (variantIds.isEmpty()) {
            return described;
        }
        lookup.describe(variantIds.stream().map(VariantId::of).toList())
            .forEach((variant, description) -> described.put(variant.value(), description));
        return described;
    }
}
