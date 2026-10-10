package com.pijamaparty.store.catalog.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A product and its variants (UC-06). It keeps every size and color combination unique, its slug following its name,
 * and a published product sellable: it is published only with an active priced variant, and that last one cannot be
 * deactivated while it stays published.
 */
public final class Product {

    private static final Comparator<Variant> DISPLAY_ORDER =
        Comparator.comparingInt(Variant::sizePosition).thenComparingInt(Variant::colorPosition);

    private final ProductId id;
    private ProductDetails details;
    private Slug slug;
    private boolean published;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Variant> variants;

    private Product(ProductId id, ProductDetails details, Slug slug, boolean published, Instant createdAt,
                    Instant updatedAt, List<Variant> variants) {
        this.id = Objects.requireNonNull(id, "id");
        this.details = Objects.requireNonNull(details, "details");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.published = published;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.variants = new ArrayList<>(variants);
        long combinations = this.variants.stream().map(v -> new VariantMatrix.Combination(v.size(), v.color()))
            .distinct().count();
        if (combinations != this.variants.size()) {
            throw new IllegalArgumentException("two variants share size and color");
        }
    }

    /** UC-06: a new product is hidden and has no variants; its slug comes from its name. */
    public static Product create(ProductId id, ProductDetails details, Instant now) {
        return new Product(id, details, details.name().slug(), false, now, now, List.of());
    }

    /** Rebuilds a stored product; only persistence adapters call it. */
    public static Product restore(ProductId id, ProductDetails details, Slug slug, boolean published,
                                  Instant createdAt, Instant updatedAt, List<Variant> variants) {
        return new Product(id, details, slug, published, createdAt, updatedAt,
            variants.stream().map(Variant::copy).toList());
    }

    /** UC-06: changing the name regenerates the slug, as for a category. */
    public void update(ProductDetails newDetails, Instant now) {
        Objects.requireNonNull(newDetails, "newDetails");
        if (!newDetails.equals(details)) {
            details = newDetails;
            slug = newDetails.name().slug();
            updatedAt = now;
        }
    }

    /**
     * UC-06: creates the size × color combinations the product does not have yet, each active and without a price,
     * and puts the requested sizes and colors first in the display order. Returns the variants it created.
     */
    public MatrixOutcome generateVariants(VariantMatrix.Request request, VariantIdentities identities, Instant now) {
        Set<VariantMatrix.Combination> existing = variants.stream()
            .map(variant -> new VariantMatrix.Combination(variant.size(), variant.color()))
            .collect(Collectors.toSet());
        return switch (VariantMatrix.plan(sizes(), colors(), existing, request)) {
            case VariantMatrix.Plan.Rejected rejected -> new MatrixOutcome.Rejected(rejected.problem());
            case VariantMatrix.Plan.Ready plan -> {
                Map<Size, Integer> sizePositions = positions(plan.sizeOrder());
                Map<Color, Integer> colorPositions = positions(plan.colorOrder());
                variants.forEach(variant -> variant.moveTo(sizePositions.get(variant.size()),
                    colorPositions.get(variant.color()), now));
                List<Variant> created = plan.missing().stream()
                    .map(combination -> Variant.create(identities.nextId(), identities.nextSku(),
                        combination.size(), sizePositions.get(combination.size()),
                        combination.color(), colorPositions.get(combination.color()), now))
                    .toList();
                variants.addAll(created);
                yield new MatrixOutcome.Generated(created.stream().map(Variant::copy).toList());
            }
        };
    }

    /**
     * UC-06: sets a variant's price (it can change, never be removed) and whether it is active. Both are optional;
     * nothing changes unless every requested change is allowed.
     */
    public VariantOutcome changeVariant(VariantId variantId, Optional<Money> price, Optional<Boolean> active,
                                        Instant now) {
        Optional<Variant> found = variants.stream().filter(variant -> variant.id().equals(variantId)).findFirst();
        if (found.isEmpty()) {
            return new VariantOutcome.UnknownVariant();
        }
        Variant variant = found.get();
        if (price.isPresent() && !Variant.isValidPrice(price.get())) {
            return new VariantOutcome.InvalidPrice();
        }
        boolean deactivating = active.isPresent() && !active.get();
        boolean lastSellable = variant.isActiveAndPriced()
            && variants.stream().filter(Variant::isActiveAndPriced).count() == 1;
        if (published && deactivating && lastSellable) {
            return new VariantOutcome.LastActivePricedVariant();
        }
        price.ifPresent(newPrice -> variant.changePrice(newPrice, now));
        active.ifPresent(newActive -> variant.changeActive(newActive, now));
        return new VariantOutcome.Changed();
    }

    /** UC-06: needs an active priced variant. Publishing a published product changes nothing. */
    public PublishOutcome publish(Instant now) {
        if (published) {
            return new PublishOutcome.Published();
        }
        if (variants.stream().noneMatch(Variant::isActiveAndPriced)) {
            return new PublishOutcome.NoActivePricedVariant();
        }
        published = true;
        updatedAt = now;
        return new PublishOutcome.Published();
    }

    /** UC-06: takes the product out of the shop; hiding a hidden product changes nothing. */
    public void hide(Instant now) {
        if (published) {
            published = false;
            updatedAt = now;
        }
    }

    public Product copy() {
        return new Product(id, details, slug, published, createdAt, updatedAt,
            variants.stream().map(Variant::copy).toList());
    }

    /** The product's sizes in display order. */
    public List<Size> sizes() {
        return variants.stream().sorted(Comparator.comparingInt(Variant::sizePosition)).map(Variant::size)
            .distinct().toList();
    }

    /** The product's colors in display order. */
    public List<Color> colors() {
        return variants.stream().sorted(Comparator.comparingInt(Variant::colorPosition)).map(Variant::color)
            .distinct().toList();
    }

    /** Every variant, by size and then color position. */
    public List<Variant> variants() {
        return variants.stream().sorted(DISPLAY_ORDER).toList();
    }

    private static <T> Map<T, Integer> positions(List<T> order) {
        Map<T, Integer> positions = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            positions.put(order.get(i), i);
        }
        return positions;
    }

    public ProductId id() {
        return id;
    }

    public ProductDetails details() {
        return details;
    }

    public Slug slug() {
        return slug;
    }

    public boolean published() {
        return published;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public sealed interface MatrixOutcome {

        /** {@code created} holds only the new variants; an empty list means the product had them all. */
        record Generated(List<Variant> created) implements MatrixOutcome {

            public Generated {
                created = List.copyOf(created);
            }
        }

        record Rejected(VariantMatrix.Problem problem) implements MatrixOutcome {}
    }

    public sealed interface VariantOutcome {

        record Changed() implements VariantOutcome {}

        record UnknownVariant() implements VariantOutcome {}

        record InvalidPrice() implements VariantOutcome {}

        /** The variant is the last active priced one of a published product: hide the product first. */
        record LastActivePricedVariant() implements VariantOutcome {}
    }

    public sealed interface PublishOutcome {

        record Published() implements PublishOutcome {}

        record NoActivePricedVariant() implements PublishOutcome {}
    }
}
