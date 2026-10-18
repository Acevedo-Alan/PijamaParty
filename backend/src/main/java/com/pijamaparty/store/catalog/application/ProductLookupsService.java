package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.ProductNames;
import com.pijamaparty.store.catalog.api.ProductVariantIds;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-24, UC-28: what the reviews module needs to know of a product, published or not. */
@Service
class ProductLookupsService implements ProductVariantIds, ProductNames {

    private final LoadProducts products;

    ProductLookupsService(LoadProducts products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<UUID>> ofProduct(UUID productId) {
        return products.findById(ProductId.of(productId)).map(product -> product.variants().stream()
            .map(Variant::id)
            .map(VariantId::value)
            .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> productIds) {
        // A customer's data download names the few products it reviewed: one lookup each is enough.
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : Set.copyOf(productIds)) {
            products.findById(ProductId.of(id)).map(Product::details)
                .ifPresent(details -> names.put(id, details.name().value()));
        }
        return Map.copyOf(names);
    }
}
