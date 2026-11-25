package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Named;
import com.pijamaparty.store.backoffice.domain.SetupFacts.Thing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Fake of {@link CatalogSetupFacts} over categories and products the test adds; no banner published at first. */
public class InMemoryCatalogSetupFacts implements CatalogSetupFacts {

    private record Category(UUID id, String name, int position, boolean active, boolean sizeGuide) {}

    private record Product(UUID id, UUID category, String name, boolean published, int minutesAgo, boolean photo) {}

    private final List<Category> categories = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private boolean bannerPublished;

    public UUID category(String name, int position, boolean active, boolean sizeGuide) {
        UUID id = UUID.randomUUID();
        categories.add(new Category(id, name, position, active, sizeGuide));
        return id;
    }

    public UUID product(UUID category, String name, boolean published, int minutesAgo, boolean photo) {
        UUID id = UUID.randomUUID();
        products.add(new Product(id, category, name, published, minutesAgo, photo));
        return id;
    }

    public void banner(boolean published) {
        bannerPublished = published;
    }

    @Override
    public Named categoriesWithoutSizeGuide(int limit) {
        List<Thing> missing = categories.stream()
            .filter(category -> category.active() && !category.sizeGuide())
            .filter(category -> products.stream()
                .anyMatch(product -> product.category().equals(category.id()) && product.published()))
            .sorted(Comparator.comparingInt(Category::position).thenComparing(Category::name))
            .map(category -> new Thing(category.id(), category.name()))
            .toList();
        return new Named(missing.stream().limit(limit).toList(), missing.size());
    }

    @Override
    public Named publishedProductsWithoutPhoto(int limit) {
        List<Thing> missing = products.stream()
            .filter(product -> product.published() && !product.photo())
            .sorted(Comparator.comparingInt(Product::minutesAgo))
            .map(product -> new Thing(product.id(), product.name()))
            .toList();
        return new Named(missing.stream().limit(limit).toList(), missing.size());
    }

    @Override
    public boolean homeBannerPublished() {
        return bannerPublished;
    }
}
