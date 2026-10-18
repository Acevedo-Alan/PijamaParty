package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.reviews.application.ProductNames;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** {@link ProductNames} through the public API of the catalog. */
@Component
class CatalogProductNames implements ProductNames {

    private final com.pijamaparty.store.catalog.api.ProductNames catalogNames;

    CatalogProductNames(com.pijamaparty.store.catalog.api.ProductNames catalogNames) {
        this.catalogNames = catalogNames;
    }

    @Override
    public Map<ProductId, String> namesOf(Collection<ProductId> products) {
        Map<ProductId, String> names = new HashMap<>();
        catalogNames.namesOf(products.stream().map(ProductId::value).toList())
            .forEach((id, name) -> names.put(ProductId.of(id), name));
        return Map.copyOf(names);
    }
}
