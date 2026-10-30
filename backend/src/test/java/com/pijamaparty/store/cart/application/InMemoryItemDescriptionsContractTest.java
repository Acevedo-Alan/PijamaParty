package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryItemDescriptionsContractTest extends ItemDescriptionsContractTest<InMemoryItemDescriptions> {

    private final Map<VariantId, ItemDescription> given = new HashMap<>();

    @Override
    protected InMemoryItemDescriptions createSubject() {
        return new InMemoryItemDescriptions();
    }

    @Override
    protected void givenVariant(VariantId variantId, String productName, String slug, String size, String color,
                                String sku, boolean sold) {
        ItemDescription description = new ItemDescription(productName, slug, size, color, sku, Optional.empty());
        given.put(variantId, description);
        subject.add(variantId, description);
    }

    @Override
    protected void givenPhotoOfItsColor(VariantId variantId, String storageKey) {
        ItemDescription description = given.get(variantId);
        subject.add(variantId, new ItemDescription(description.productName(), description.productSlug(),
            description.size(), description.color(), description.sku(), Optional.of(new ItemDescription.Photo(PLACEHOLDER,
                List.of(400, 600, 800, 1200).stream()
                    .map(width -> new ItemDescription.PhotoFile(width, storageKey + "-" + width + ".webp"))
                    .toList()))));
    }
}
