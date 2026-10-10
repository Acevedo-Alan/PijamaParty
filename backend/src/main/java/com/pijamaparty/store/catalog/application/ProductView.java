package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Optional;

/** What the back office shows of a product: its details, sizes and colors in display order, and its variants. */
public record ProductView(ProductId id, CategoryId categoryId, String name, String slug, String description,
                          String material, Season season, Audience audience, boolean published, List<String> sizes,
                          List<String> colors, List<VariantView> variants) {

    public ProductView {
        sizes = List.copyOf(sizes);
        colors = List.copyOf(colors);
        variants = List.copyOf(variants);
    }

    static ProductView of(Product product) {
        var details = product.details();
        return new ProductView(product.id(), details.categoryId(), details.name().value(), product.slug().value(),
            details.description().value(), details.material().value(), details.season(), details.audience(),
            product.published(), product.sizes().stream().map(Size::value).toList(),
            product.colors().stream().map(Color::value).toList(),
            product.variants().stream().map(VariantView::of).toList());
    }

    /** A variant by size and then color position; no price until the administrator sets one. */
    public record VariantView(VariantId id, String sku, String size, String color, Optional<Money> price,
                              boolean active) {

        static VariantView of(Variant variant) {
            return new VariantView(variant.id(), variant.sku().value(), variant.size().value(),
                variant.color().value(), variant.price(), variant.active());
        }
    }
}
