package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.reviews.application.ProductLinks;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** {@link ProductLinks} through the public API of the catalog. */
@Component
class CatalogProductLinks implements ProductLinks {

    private final com.pijamaparty.store.catalog.api.ProductLinks catalogLinks;

    CatalogProductLinks(com.pijamaparty.store.catalog.api.ProductLinks catalogLinks) {
        this.catalogLinks = catalogLinks;
    }

    @Override
    public Map<ProductId, ProductLink> linksOf(Collection<ProductId> products) {
        Map<ProductId, ProductLink> links = new HashMap<>();
        catalogLinks.linksOf(products.stream().map(ProductId::value).toList())
            .forEach((id, link) -> links.put(ProductId.of(id), new ProductLink(link.name(), link.slug())));
        return Map.copyOf(links);
    }
}
