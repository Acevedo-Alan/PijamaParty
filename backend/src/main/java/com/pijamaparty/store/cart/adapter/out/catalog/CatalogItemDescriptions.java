package com.pijamaparty.store.cart.adapter.out.catalog;

import com.pijamaparty.store.cart.application.ItemDescription;
import com.pijamaparty.store.cart.application.ItemDescriptions;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.api.VariantDescriptions;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** What a cart line shows of its variant, through the catalog's public API, in the caller's transaction. */
@Component
class CatalogItemDescriptions implements ItemDescriptions {

    private final VariantDescriptions variantDescriptions;

    CatalogItemDescriptions(VariantDescriptions variantDescriptions) {
        this.variantDescriptions = variantDescriptions;
    }

    @Override
    public Map<VariantId, ItemDescription> describe(Collection<VariantId> variantIds) {
        Map<VariantId, ItemDescription> described = new HashMap<>();
        if (variantIds.isEmpty()) {
            return described;
        }
        variantDescriptions.describe(variantIds.stream().map(VariantId::value).toList())
            .forEach((id, description) -> described.put(VariantId.of(id), toItem(description)));
        return described;
    }

    private static ItemDescription toItem(VariantDescription description) {
        return new ItemDescription(description.productName(), description.productSlug(), description.size(),
            description.color(), description.sku(), description.photo().map(photo -> new ItemDescription.Photo(photo.placeholder(),
                photo.files().stream()
                    .map(file -> new ItemDescription.PhotoFile(file.width(), file.fileName()))
                    .toList())));
    }
}
