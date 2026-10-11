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
 * A product, its variants and its photos (UC-06). It keeps every size and color combination unique, its slug
 * following its name, and a published product sellable: it is published only with an active priced variant, and that
 * last one cannot be deactivated while it stays published. It holds at most {@link #MAX_IMAGES} photos, each of one of
 * its colors or of the whole product, and one per upload key.
 */
public final class Product {

    /** UC-06 (proposed): the most photos a product has. */
    public static final int MAX_IMAGES = 8;

    private static final Comparator<Variant> DISPLAY_ORDER =
        Comparator.comparingInt(Variant::sizePosition).thenComparingInt(Variant::colorPosition);

    private final ProductId id;
    private ProductDetails details;
    private Slug slug;
    private boolean published;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Variant> variants;
    /** By position, which is always the index. */
    private final List<ProductImage> images;

    private Product(ProductId id, ProductDetails details, Slug slug, boolean published, Instant createdAt,
                    Instant updatedAt, List<Variant> variants, List<ProductImage> images) {
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
        this.images = new ArrayList<>(images.stream().sorted(Comparator.comparingInt(ProductImage::position)).toList());
        if (this.images.stream().map(ProductImage::uploadKey).distinct().count() != this.images.size()) {
            throw new IllegalArgumentException("two photos share an upload key");
        }
        renumberImages();
    }

    /** UC-06: a new product is hidden and has no variants or photos; its slug comes from its name. */
    public static Product create(ProductId id, ProductDetails details, Instant now) {
        return new Product(id, details, details.name().slug(), false, now, now, List.of(), List.of());
    }

    /** Rebuilds a stored product; only persistence adapters call it. */
    public static Product restore(ProductId id, ProductDetails details, Slug slug, boolean published,
                                  Instant createdAt, Instant updatedAt, List<Variant> variants,
                                  List<ProductImage> images) {
        return new Product(id, details, slug, published, createdAt, updatedAt,
            variants.stream().map(Variant::copy).toList(), images.stream().map(ProductImage::copy).toList());
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

    /**
     * UC-06: whether a photo with this upload key, file and color can be added, without adding it. The upload checks
     * this before converting the file, then adds the photo with {@link #addImage}, which checks again.
     */
    public ImageUpload checkUpload(UploadKey uploadKey, ContentHash contentHash, Optional<Color> color) {
        Optional<ProductImage> uploaded = images.stream()
            .filter(image -> image.uploadKey().equals(uploadKey)).findFirst();
        if (uploaded.isPresent()) {
            // A retry is answered even when the product is full or the photo's color was another.
            return uploaded.get().contentHash().equals(contentHash)
                ? new ImageUpload.AlreadyUploaded(uploaded.get().copy())
                : new ImageUpload.KeyReused();
        }
        if (images.size() >= MAX_IMAGES) {
            return new ImageUpload.TooManyImages();
        }
        if (color.isPresent() && !colors().contains(color.get())) {
            return new ImageUpload.UnknownColor();
        }
        return new ImageUpload.Ready();
    }

    /** UC-06: adds the photo last, unless {@link #checkUpload} refuses it; never answers {@code Ready}. */
    public ImageUpload addImage(NewImage image, Instant now) {
        ImageUpload check = checkUpload(image.uploadKey(), image.contentHash(), image.color());
        if (!(check instanceof ImageUpload.Ready)) {
            return check;
        }
        if (images.stream().anyMatch(existing -> existing.id().equals(image.id()))) {
            throw new IllegalArgumentException("the photo id is already used");
        }
        ProductImage added = ProductImage.create(image, images.size(), now);
        images.add(added);
        return new ImageUpload.Added(added.copy());
    }

    /**
     * UC-06: puts the photos in the given order, which must name every photo exactly once (as for categories);
     * otherwise nothing changes and it returns false.
     */
    public boolean reorderImages(List<ImageId> order) {
        Map<ImageId, ProductImage> byId = images.stream()
            .collect(Collectors.toMap(ProductImage::id, image -> image));
        if (order.size() != byId.size() || !byId.keySet().equals(Set.copyOf(order))) {
            return false;
        }
        images.clear();
        order.forEach(imageId -> images.add(byId.get(imageId)));
        renumberImages();
        return true;
    }

    /** UC-06: removes a photo and closes the gap it leaves; empty when the product has no such photo. */
    public Optional<ProductImage> removeImage(ImageId imageId) {
        Optional<ProductImage> found = images.stream().filter(image -> image.id().equals(imageId)).findFirst();
        found.ifPresent(image -> {
            images.remove(image);
            renumberImages();
        });
        return found.map(ProductImage::copy);
    }

    private void renumberImages() {
        for (int position = 0; position < images.size(); position++) {
            images.get(position).moveTo(position);
        }
    }

    public Product copy() {
        return new Product(id, details, slug, published, createdAt, updatedAt,
            variants.stream().map(Variant::copy).toList(), images.stream().map(ProductImage::copy).toList());
    }

    /** The photos by position. */
    public List<ProductImage> images() {
        return images.stream().map(ProductImage::copy).toList();
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

    /**
     * A photo to add: its files are already stored under {@code storageKey}. {@code color} is empty for a photo of the
     * whole product.
     */
    public record NewImage(ImageId id, Optional<Color> color, StorageKey storageKey, ImagePlaceholder placeholder,
                           UploadKey uploadKey, ContentHash contentHash) {

        public NewImage {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(storageKey, "storageKey");
            Objects.requireNonNull(placeholder, "placeholder");
            Objects.requireNonNull(uploadKey, "uploadKey");
            Objects.requireNonNull(contentHash, "contentHash");
        }
    }

    public sealed interface ImageUpload {

        /** {@link #checkUpload} only: the photo can be added. */
        record Ready() implements ImageUpload {}

        /** {@link #addImage} only: the photo was added. */
        record Added(ProductImage image) implements ImageUpload {}

        /** A retry: the photo of this upload key and file is already there. */
        record AlreadyUploaded(ProductImage image) implements ImageUpload {}

        /** The upload key belongs to a photo of another file. */
        record KeyReused() implements ImageUpload {}

        record TooManyImages() implements ImageUpload {}

        /** The product has no variant of that color. */
        record UnknownColor() implements ImageUpload {}
    }

    public sealed interface PublishOutcome {

        record Published() implements PublishOutcome {}

        record NoActivePricedVariant() implements PublishOutcome {}
    }
}
