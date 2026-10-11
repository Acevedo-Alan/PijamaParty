package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fake of {@link LoadProducts}, {@link SaveProduct} and {@link ProductSummaries}, with the database's rules: unique
 * slug, unique SKU and an existing category. Locking reads are plain reads; the fake serializes its writes.
 */
public class InMemoryProductStore implements LoadProducts, SaveProduct, ProductSummaries {

    private final InMemoryStore<ProductId, Product> products = InMemoryStore.copying(Product::id, Product::copy);
    private final LoadCategories categories;

    /** {@code categories} stands for the category table that the product's foreign key points to. */
    public InMemoryProductStore(LoadCategories categories) {
        this.categories = categories;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return products.findById(id);
    }

    @Override
    public Optional<Product> findBySlug(Slug slug) {
        return products.findAll(product -> product.slug().equals(slug)).stream().findFirst();
    }

    @Override
    public Optional<Product> lockById(ProductId id) {
        return findById(id);
    }

    @Override
    public Optional<Product> lockByVariantId(VariantId id) {
        return products.findAll(product -> product.variants().stream().anyMatch(v -> v.id().equals(id))).stream()
            .findFirst();
    }

    @Override
    public List<ProductSummaryView> list(int offset, int limit) {
        return products.findAll().stream()
            .sorted(Comparator.comparing(Product::createdAt).thenComparing(product -> product.id().value())
                .reversed())
            .skip(offset)
            .limit(limit)
            .map(product -> new ProductSummaryView(product.id(), product.details().categoryId(),
                product.details().name().value(), product.slug().value(), product.published(),
                product.variants().size()))
            .toList();
    }

    @Override
    public synchronized SaveProduct.Outcome add(Product product) {
        return write(product);
    }

    @Override
    public synchronized SaveProduct.Outcome update(Product product) {
        if (products.findById(product.id()).isEmpty()) {
            throw new IllegalStateException("products are never deleted");
        }
        return write(product);
    }

    /** Copies of every stored product, for the fakes of the read side ({@link InMemoryProductListing}). */
    public List<Product> all() {
        return products.findAll();
    }

    /** Test arrangement: stores the product as it is, like {@link #add} without the result. */
    public void save(Product product) {
        SaveProduct.Outcome outcome = add(product);
        if (outcome != SaveProduct.Outcome.SAVED) {
            throw new IllegalArgumentException(outcome.name());
        }
    }

    private SaveProduct.Outcome write(Product product) {
        if (categories.findById(product.details().categoryId()).isEmpty()) {
            return SaveProduct.Outcome.UNKNOWN_CATEGORY;
        }
        List<Product> others = products.findAll(other -> !other.id().equals(product.id()));
        if (others.stream().anyMatch(other -> other.slug().equals(product.slug()))) {
            return SaveProduct.Outcome.SLUG_ALREADY_USED;
        }
        Set<String> otherSkus = others.stream().flatMap(other -> other.variants().stream())
            .map(variant -> variant.sku().value()).collect(Collectors.toSet());
        List<String> skus = product.variants().stream().map(Variant::sku).map(sku -> sku.value()).toList();
        if (skus.stream().distinct().count() != skus.size() || skus.stream().anyMatch(otherSkus::contains)) {
            return SaveProduct.Outcome.SKU_ALREADY_USED;
        }
        products.save(product);
        return SaveProduct.Outcome.SAVED;
    }
}
