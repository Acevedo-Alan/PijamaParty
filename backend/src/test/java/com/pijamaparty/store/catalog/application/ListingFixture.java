package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Sku;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.catalog.domain.VariantIdentities;
import com.pijamaparty.store.catalog.domain.VariantMatrix;
import com.pijamaparty.store.platform.domain.Money;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Builds categories and products for the tests of the public catalog (UC-01, UC-02). */
public final class ListingFixture {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    public static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    public static final String PLACEHOLDER = "data:image/webp;base64,UklGRg==";

    private ListingFixture() {
    }

    public static Category category(String name, boolean active) {
        Category category = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), List.of(), T0);
        if (!active) {
            category.deactivate(T0);
        }
        return category;
    }

    /**
     * A published product of a summer cotton pajama for women, created {@code minutes} after {@link #T0}, with
     * every size × color variant active and priced at {@code cents}; {@code changes} adjust it before publishing.
     */
    public static Product product(CategoryId category, String name, int minutes, List<String> sizes,
                                  List<String> colors, long cents, Consumer<Builder> changes) {
        var builder = new Builder(category, name, T0.plus(Duration.ofMinutes(minutes)), sizes, colors, cents);
        changes.accept(builder);
        return builder.build();
    }

    public static Product product(CategoryId category, String name, int minutes, List<String> sizes,
                                  List<String> colors, long cents) {
        return product(category, name, minutes, sizes, colors, cents, builder -> { });
    }

    /** The variant of {@code product} with this size and color. */
    public static VariantId variant(Product product, String size, String color) {
        return product.variants().stream()
            .filter(variant -> variant.size().value().equals(size) && variant.color().value().equals(color))
            .map(Variant::id)
            .findFirst()
            .orElseThrow();
    }

    public static final class Builder {

        private final CategoryId category;
        private final String name;
        private final Instant createdAt;
        private final List<String> sizes;
        private final List<String> colors;
        private final long cents;
        private final List<Consumer<Product>> variantChanges = new ArrayList<>();
        private final List<String> unpriced = new ArrayList<>();
        private String description = "";
        private String material = "Algodón";
        private Season season = Season.SUMMER;
        private Audience audience = Audience.WOMEN;
        private boolean published = true;
        private int images;

        private Builder(CategoryId category, String name, Instant createdAt, List<String> sizes, List<String> colors,
                        long cents) {
            this.category = category;
            this.name = name;
            this.createdAt = createdAt;
            this.sizes = sizes;
            this.colors = colors;
            this.cents = cents;
        }

        public Builder description(String newDescription) {
            description = newDescription;
            return this;
        }

        public Builder material(String newMaterial) {
            material = newMaterial;
            return this;
        }

        public Builder season(Season newSeason) {
            season = newSeason;
            return this;
        }

        public Builder audience(Audience newAudience) {
            audience = newAudience;
            return this;
        }

        public Builder hidden() {
            published = false;
            return this;
        }

        public Builder price(String size, String color, long newCents) {
            variantChanges.add(product -> product.changeVariant(variant(product, size, color),
                Optional.of(Money.of(newCents)), Optional.empty(), createdAt));
            return this;
        }

        public Builder inactive(String size, String color) {
            variantChanges.add(product -> product.changeVariant(variant(product, size, color), Optional.empty(),
                Optional.of(false), createdAt));
            return this;
        }

        public Builder unpriced(String size, String color) {
            unpriced.add(size + "/" + color);
            return this;
        }

        /** Adds photos of the whole product; the first one is the one the grid shows. */
        public Builder images(int count) {
            images = count;
            return this;
        }

        private Product build() {
            Product product = Product.create(ProductId.of(UUID.randomUUID()), new ProductDetails(category,
                new ProductName(name), new Description(description), new Material(material), season, audience),
                createdAt);
            String skuPrefix = "PP-L" + product.id().value().toString().substring(0, 8) + "-";
            var identities = new VariantIdentities() {
                private int next;

                @Override
                public VariantId nextId() {
                    return VariantId.of(UUID.randomUUID());
                }

                @Override
                public Sku nextSku() {
                    return new Sku(skuPrefix + (++next));
                }
            };
            var request = ((VariantMatrix.Parsed.Valid) VariantMatrix.parse(sizes, colors)).request();
            product.generateVariants(request, identities, createdAt);
            product.variants().stream()
                .filter(variant -> !unpriced.contains(variant.size().value() + "/" + variant.color().value()))
                .forEach(variant -> product.changeVariant(variant.id(), Optional.of(Money.of(cents)),
                    Optional.empty(), createdAt));
            variantChanges.forEach(change -> change.accept(product));
            for (int i = 0; i < images; i++) {
                product.addImage(new Product.NewImage(ImageId.of(UUID.randomUUID()), Optional.<Color>empty(),
                    StorageKey.of(UUID.randomUUID()), new ImagePlaceholder(PLACEHOLDER), UploadKey.of(UUID.randomUUID()),
                    ContentHash.of((name + i).getBytes(StandardCharsets.UTF_8))), createdAt);
            }
            if (published) {
                product.publish(createdAt);
            }
            return product;
        }
    }
}
