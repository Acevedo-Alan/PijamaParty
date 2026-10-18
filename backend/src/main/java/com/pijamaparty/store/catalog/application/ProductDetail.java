package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a visitor sees of a product (UC-03): only its sellable variants, each with its price and available stock, the
 * sizes and colors of those variants in display order, and the photos of the whole product and of those colors by
 * position. {@code fromPrice} is the lowest of their prices, as in the grid. {@code id} is what the customer's
 * review of the product refers to (UC-24). {@code sizeGuide} is the guide of the product's category, if it has one
 * (RNF-31.5).
 */
public record ProductDetail(ProductId id, String slug, String name, String description, String material,
                            Season season, Audience audience, Money fromPrice, List<String> sizes,
                            List<String> colors, List<VariantDetail> variants, List<ProductView.ImageView> images,
                            Optional<SizeGuideView> sizeGuide) {

    public ProductDetail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromPrice, "fromPrice");
        sizes = List.copyOf(sizes);
        colors = List.copyOf(colors);
        variants = List.copyOf(variants);
        images = List.copyOf(images);
        Objects.requireNonNull(sizeGuide, "sizeGuide");
    }

    /** {@code available} by variant; a variant missing from it has nothing available. */
    static ProductDetail of(Product product, Map<VariantId, Integer> available, Optional<SizeGuideView> sizeGuide) {
        List<Variant> sellable = product.sellableVariants();
        if (sellable.isEmpty()) {
            throw new IllegalArgumentException("the product has nothing to sell");
        }
        Set<Color> colors = sellable.stream().map(Variant::color).collect(Collectors.toSet());
        var details = product.details();
        return new ProductDetail(product.id(), product.slug().value(), details.name().value(), details.description().value(),
            details.material().value(), details.season(), details.audience(),
            sellable.stream().map(variant -> variant.price().orElseThrow()).min(Comparator.naturalOrder()).orElseThrow(),
            product.sizes().stream().filter(size -> sellable.stream().anyMatch(v -> v.size().equals(size)))
                .map(Size::value).toList(),
            product.colors().stream().filter(colors::contains).map(Color::value).toList(),
            sellable.stream().map(variant -> VariantDetail.of(variant, available.getOrDefault(variant.id(), 0)))
                .toList(),
            product.images().stream().filter(image -> image.color().map(colors::contains).orElse(true))
                .map(ProductView.ImageView::of).toList(),
            sizeGuide);
    }

    /** A sellable variant by size and then color position. */
    public record VariantDetail(VariantId id, String sku, String size, String color, Money price, int available) {

        static VariantDetail of(Variant variant, int available) {
            return new VariantDetail(variant.id(), variant.sku().value(), variant.size().value(),
                variant.color().value(), variant.price().orElseThrow(), available);
        }
    }
}
