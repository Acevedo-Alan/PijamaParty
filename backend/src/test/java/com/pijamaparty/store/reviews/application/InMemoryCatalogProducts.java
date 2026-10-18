package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link ProductVariants} and {@link ProductNames}: a product exists once {@link #add} gives it. */
public class InMemoryCatalogProducts implements ProductVariants, ProductNames {

    private record Product(String name, Set<UUID> variants) {}

    private final Map<ProductId, Product> products = new ConcurrentHashMap<>();

    public void add(ProductId product, String name, Set<UUID> variants) {
        products.put(product, new Product(name, Set.copyOf(variants)));
    }

    @Override
    public Optional<Set<UUID>> ofProduct(ProductId product) {
        return Optional.ofNullable(products.get(product)).map(Product::variants);
    }

    @Override
    public Map<ProductId, String> namesOf(Collection<ProductId> productIds) {
        Map<ProductId, String> names = new HashMap<>();
        productIds.forEach(id -> Optional.ofNullable(products.get(id)).ifPresent(p -> names.put(id, p.name())));
        return Map.copyOf(names);
    }
}
