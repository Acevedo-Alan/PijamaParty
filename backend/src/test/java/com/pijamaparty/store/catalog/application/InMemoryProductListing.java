package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fake of {@link ProductListingQuery}, {@link FilterOptionsQuery} and {@link ListedProductSlugs} over the category and
 * product fakes, with the inventory's available stock set by {@link #setAvailable}. Ids break ties as PostgreSQL
 * orders UUIDs (by their text).
 */
public class InMemoryProductListing implements ProductListingQuery, FilterOptionsQuery, ListedProductSlugs {

    private static final Comparator<Product> NEWEST_FIRST = Comparator.comparing(Product::createdAt)
        .thenComparing((Product product) -> product.id().value().toString())
        .reversed();

    private final LoadCategories categories;
    private final InMemoryProductStore products;
    private final Map<VariantId, Integer> available = new ConcurrentHashMap<>();

    public InMemoryProductListing(LoadCategories categories, InMemoryProductStore products) {
        this.categories = categories;
        this.products = products;
    }

    /** The inventory's available stock of a variant; a variant never set has none. */
    public void setAvailable(VariantId variant, int units) {
        available.put(variant, units);
    }

    @Override
    public List<ListedProduct> list(ProductFilter filter, ProductSort sort, int offset, int limit) {
        Comparator<Product> order = switch (sort) {
            case NEWEST -> NEWEST_FIRST;
            case PRICE_ASC -> Comparator.comparing(InMemoryProductListing::fromPrice).thenComparing(NEWEST_FIRST);
            case PRICE_DESC -> Comparator.comparing(InMemoryProductListing::fromPrice).reversed()
                .thenComparing(NEWEST_FIRST);
        };
        return listed(filter.category()).stream()
            .filter(product -> matches(product, filter))
            .sorted(order)
            .skip(offset)
            .limit(limit)
            .map(this::toListed)
            .toList();
    }

    @Override
    public List<String> newestFirst(int limit) {
        return listed(Optional.empty()).stream()
            .sorted(NEWEST_FIRST)
            .limit(limit)
            .map(product -> product.slug().value())
            .toList();
    }

    @Override
    public long count(ProductFilter filter) {
        return listed(filter.category()).stream().filter(product -> matches(product, filter)).count();
    }

    @Override
    public FilterOptions options(Optional<CategoryId> category) {
        List<Product> listed = listed(category);
        List<Variant> inStock = listed.stream().flatMap(product -> sellable(product).stream())
            .filter(this::hasStock).toList();
        List<Money> prices = listed.stream().map(InMemoryProductListing::fromPrice).sorted().toList();
        return new FilterOptions(
            byPositionThenText(inStock, variant -> variant.size().value(), Variant::sizePosition),
            byPositionThenText(inStock, variant -> variant.color().value(), Variant::colorPosition),
            listed.stream().map(product -> product.details().material().value()).distinct().sorted().toList(),
            listed.stream().map(product -> product.details().season()).distinct().sorted().toList(),
            listed.stream().map(product -> product.details().audience()).distinct().sorted().toList(),
            prices.stream().findFirst(), prices.isEmpty() ? Optional.empty() : Optional.of(prices.getLast()));
    }

    /** Published products of an active category with an active priced variant. */
    private List<Product> listed(Optional<CategoryId> category) {
        List<CategoryId> active = categories.findAll().stream().filter(Category::active).map(Category::id).toList();
        return products.all().stream()
            .filter(Product::published)
            .filter(product -> active.contains(product.details().categoryId()))
            .filter(product -> category.isEmpty() || category.get().equals(product.details().categoryId()))
            .filter(product -> !sellable(product).isEmpty())
            .toList();
    }

    private boolean matches(Product product, ProductFilter filter) {
        Money from = fromPrice(product);
        boolean variantMatches = filter.sizes().isEmpty() && filter.colors().isEmpty()
            || sellable(product).stream().filter(this::hasStock).anyMatch(variant ->
                (filter.sizes().isEmpty() || filter.sizes().contains(variant.size()))
                    && (filter.colors().isEmpty() || filter.colors().contains(variant.color())));
        return variantMatches
            && (filter.materials().isEmpty() || filter.materials().contains(product.details().material()))
            && (filter.seasons().isEmpty() || filter.seasons().contains(product.details().season()))
            && (filter.audiences().isEmpty() || filter.audiences().contains(product.details().audience()))
            && filter.minPrice().map(min -> !from.isLessThan(min)).orElse(true)
            && filter.maxPrice().map(max -> !from.isGreaterThan(max)).orElse(true);
    }

    private ListedProduct toListed(Product product) {
        List<Variant> inStock = sellable(product).stream().filter(this::hasStock).toList();
        return new ListedProduct(product.slug().value(), product.details().name().value(),
            product.images().stream().findFirst()
                .map(image -> ProductListingItem.ListingImage.of(image.storageKey(), image.placeholder())),
            product.images().stream().skip(1).findFirst()
                .map(image -> ProductListingItem.ListingImage.of(image.storageKey(), image.placeholder())),
            fromPrice(product), inStock.isEmpty(),
            byPositionThenText(inStock, variant -> variant.color().value(), Variant::colorPosition),
            product.createdAt());
    }

    private boolean hasStock(Variant variant) {
        return available.getOrDefault(variant.id(), 0) > 0;
    }

    private static List<Variant> sellable(Product product) {
        return product.variants().stream().filter(Variant::isActiveAndPriced).toList();
    }

    private static Money fromPrice(Product product) {
        return sellable(product).stream().map(variant -> variant.price().orElseThrow()).min(Comparator.naturalOrder())
            .orElseThrow();
    }

    private static List<String> byPositionThenText(List<Variant> variants, Function<Variant, String> text,
                                                   Function<Variant, Integer> position) {
        Map<String, Integer> smallest = variants.stream()
            .collect(Collectors.toMap(text, position, Math::min));
        return smallest.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey)
            .toList();
    }
}
