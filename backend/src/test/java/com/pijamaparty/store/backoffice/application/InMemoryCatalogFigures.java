package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Fake of {@link CatalogFigures}: products and variants as the test arranges them. */
public class InMemoryCatalogFigures implements CatalogFigures {

    private record Product(String name, boolean published) {}

    private record Variant(UUID id, UUID product, String size, int position, boolean active, long available) {}

    private final Map<UUID, Product> products = new HashMap<>();
    private final List<Variant> variants = new ArrayList<>();

    /** Test arrangement: a product whose slug is its name in lowercase; answers its id. */
    public synchronized UUID product(String name, boolean published) {
        UUID id = UUID.randomUUID();
        products.put(id, new Product(name, published));
        return id;
    }

    /** Test arrangement: a variant of {@code product}; answers its id. */
    public synchronized UUID variant(UUID product, String size, int position, boolean active, long available) {
        UUID id = UUID.randomUUID();
        variants.add(new Variant(id, product, size, position, active, available));
        return id;
    }

    private List<Variant> low(int threshold) {
        return variants.stream()
            .filter(variant -> variant.active() && products.get(variant.product()).published())
            .filter(variant -> variant.available() <= threshold)
            .sorted(Comparator.comparingLong(Variant::available)
                .thenComparing(variant -> products.get(variant.product()).name())
                .thenComparingInt(Variant::position))
            .toList();
    }

    @Override
    public synchronized List<LowStockVariant> lowStock(int threshold, int limit) {
        return low(threshold).stream().limit(limit)
            .map(variant -> new LowStockVariant(variant.id(), variant.product(), products.get(variant.product()).name(),
                variant.size(), "Rosa", variant.available()))
            .toList();
    }

    @Override
    public synchronized long lowStockCount(int threshold) {
        return low(threshold).size();
    }

    @Override
    public synchronized long publishedProducts() {
        return products.values().stream().filter(Product::published).count();
    }

    @Override
    public synchronized Map<UUID, ProductOf> productsOf(Collection<UUID> variantIds) {
        Map<UUID, ProductOf> found = new HashMap<>();
        variants.stream().filter(variant -> variantIds.contains(variant.id())).forEach(variant -> {
            String name = products.get(variant.product()).name();
            found.put(variant.id(), new ProductOf(name.toLowerCase(Locale.ROOT), name));
        });
        return Map.copyOf(found);
    }
}
