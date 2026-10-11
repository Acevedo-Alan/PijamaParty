package com.pijamaparty.store.catalog.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The filters of the public catalog (UC-02). Different filters combine with AND and the values of one filter with OR;
 * an empty filter does not filter. Sizes and colors apply to one variant with available stock, and the price range
 * to the product's "from" price, bounds included.
 */
public record ProductFilter(Optional<CategoryId> category, Set<Size> sizes, Set<Color> colors,
                            Set<Material> materials, Set<Season> seasons, Set<Audience> audiences,
                            Optional<Money> minPrice, Optional<Money> maxPrice) {

    /** Bounds the query: no filter panel offers more values than this. */
    public static final int MAX_VALUES = 20;

    public ProductFilter {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(minPrice, "minPrice");
        Objects.requireNonNull(maxPrice, "maxPrice");
        sizes = bounded(sizes);
        colors = bounded(colors);
        materials = bounded(materials);
        seasons = Set.copyOf(seasons);
        audiences = Set.copyOf(audiences);
        if (minPrice.isPresent() && maxPrice.isPresent() && minPrice.get().isGreaterThan(maxPrice.get())) {
            throw new IllegalArgumentException("the minimum price is above the maximum");
        }
    }

    /** No filter at all: every listed product. */
    public static ProductFilter none() {
        return new ProductFilter(Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Optional.empty(),
            Optional.empty());
    }

    /**
     * The filter of a request, from the texts and amounts as received (repeated values count once). Empty when a
     * value cannot be a size, color or material, a filter has more than {@link #MAX_VALUES} values, a price is
     * negative or the minimum is above the maximum.
     */
    public static Optional<ProductFilter> parse(Optional<CategoryId> category, List<String> sizes, List<String> colors,
                                                List<String> materials, Set<Season> seasons, Set<Audience> audiences,
                                                Optional<Long> minPriceCents, Optional<Long> maxPriceCents) {
        Optional<Set<Size>> parsedSizes = parseAll(sizes, Size::parse);
        Optional<Set<Color>> parsedColors = parseAll(colors, Color::parse);
        Optional<Set<Material>> parsedMaterials = parseAll(materials, Material::parse);
        if (parsedSizes.isEmpty() || parsedColors.isEmpty() || parsedMaterials.isEmpty()
            || isNegative(minPriceCents) || isNegative(maxPriceCents)
            || minPriceCents.isPresent() && maxPriceCents.isPresent() && minPriceCents.get() > maxPriceCents.get()) {
            return Optional.empty();
        }
        return Optional.of(new ProductFilter(category, parsedSizes.get(), parsedColors.get(), parsedMaterials.get(),
            seasons, audiences, minPriceCents.map(Money::of), maxPriceCents.map(Money::of)));
    }

    private static <T> Optional<Set<T>> parseAll(Collection<String> raw, Function<String, Optional<T>> parser) {
        List<Optional<T>> parsed = raw.stream().map(parser).toList();
        if (parsed.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        Set<T> values = Set.copyOf(parsed.stream().map(Optional::get).toList());
        return values.size() > MAX_VALUES ? Optional.empty() : Optional.of(values);
    }

    private static boolean isNegative(Optional<Long> cents) {
        return cents.filter(value -> value < 0).isPresent();
    }

    private static <T> Set<T> bounded(Set<T> values) {
        Set<T> copy = Set.copyOf(values);
        if (copy.size() > MAX_VALUES) {
            throw new IllegalArgumentException("at most " + MAX_VALUES + " values per filter");
        }
        return copy;
    }
}
