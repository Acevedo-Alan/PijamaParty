package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-06: the photos of a product. */
class ProductImagesTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final ContentHash FILE = ContentHash.of(new byte[] {1});
    private static final ContentHash OTHER_FILE = ContentHash.of(new byte[] {2});

    private final Product product = productWithColors("Rosa", "Celeste");

    @Test
    void givenPhotos_whenAdded_thenEachGoesLast() {
        ProductImage first = added(image(Optional.empty()));
        ProductImage second = added(image(Optional.of(new Color("Rosa"))));

        assertThat(product.images()).extracting(ProductImage::id).containsExactly(first.id(), second.id());
        assertThat(product.images()).extracting(ProductImage::position).containsExactly(0, 1);
        assertThat(second.color()).contains(new Color("Rosa"));
        assertThat(second.createdAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnUploadKeyAlreadyUsed_whenTheSameFileComesAgain_thenTheStoredPhotoIsAnsweredAndNothingIsAdded() {
        Product.NewImage image = image(Optional.empty());
        ProductImage stored = added(image);
        Product.NewImage retry = new Product.NewImage(ImageId.of(UUID.randomUUID()), Optional.empty(),
            StorageKey.of(UUID.randomUUID()), image.placeholder(), image.uploadKey(), FILE);

        assertThat(product.addImage(retry, NOW)).isInstanceOfSatisfying(Product.ImageUpload.AlreadyUploaded.class,
            uploaded -> assertThat(uploaded.image().id()).isEqualTo(stored.id()));
        assertThat(product.checkUpload(image.uploadKey(), OTHER_FILE, Optional.empty()))
            .isEqualTo(new Product.ImageUpload.KeyReused());
        assertThat(product.images()).hasSize(1);
    }

    @Test
    void givenAFullProduct_whenAnotherPhotoIsAdded_thenItIsRefusedButARetryIsStillAnswered() {
        List<Product.NewImage> images = new ArrayList<>();
        for (int i = 0; i < Product.MAX_IMAGES; i++) {
            images.add(image(Optional.empty()));
            added(images.getLast());
        }

        assertThat(product.addImage(image(Optional.empty()), NOW)).isEqualTo(new Product.ImageUpload.TooManyImages());
        assertThat(product.checkUpload(images.getFirst().uploadKey(), FILE, Optional.empty()))
            .isInstanceOf(Product.ImageUpload.AlreadyUploaded.class);
        assertThat(product.images()).hasSize(Product.MAX_IMAGES);
    }

    @Test
    void givenAColorTheProductDoesNotHave_whenAPhotoOfItIsAdded_thenItIsRefused() {
        assertThat(product.addImage(image(Optional.of(new Color("Verde"))), NOW))
            .isEqualTo(new Product.ImageUpload.UnknownColor());
        assertThat(product.checkUpload(UploadKey.of(UUID.randomUUID()), FILE, Optional.of(new Color("Celeste"))))
            .isEqualTo(new Product.ImageUpload.Ready());
        assertThat(product.images()).isEmpty();
    }

    @Test
    void givenThreePhotos_whenReordered_thenTheyTakeTheNewOrder() {
        ImageId a = added(image(Optional.empty())).id();
        ImageId b = added(image(Optional.empty())).id();
        ImageId c = added(image(Optional.empty())).id();

        assertThat(product.reorderImages(List.of(c, a, b))).isTrue();

        assertThat(product.images()).extracting(ProductImage::id).containsExactly(c, a, b);
        assertThat(product.images()).extracting(ProductImage::position).containsExactly(0, 1, 2);
    }

    @Test
    void givenAnOrderThatDoesNotNameEveryPhotoOnce_whenReordering_thenNothingChanges() {
        ImageId a = added(image(Optional.empty())).id();
        ImageId b = added(image(Optional.empty())).id();

        assertThat(product.reorderImages(List.of(b))).isFalse();
        assertThat(product.reorderImages(List.of(b, b))).isFalse();
        assertThat(product.reorderImages(List.of(b, a, ImageId.of(UUID.randomUUID())))).isFalse();
        assertThat(product.reorderImages(List.of(b, ImageId.of(UUID.randomUUID())))).isFalse();

        assertThat(product.images()).extracting(ProductImage::id).containsExactly(a, b);
    }

    @Test
    void givenThreePhotos_whenTheMiddleOneIsRemoved_thenTheGapCloses() {
        ImageId a = added(image(Optional.empty())).id();
        ProductImage b = added(image(Optional.empty()));
        ImageId c = added(image(Optional.empty())).id();

        assertThat(product.removeImage(b.id())).hasValueSatisfying(removed ->
            assertThat(removed.storageKey()).isEqualTo(b.storageKey()));

        assertThat(product.images()).extracting(ProductImage::id).containsExactly(a, c);
        assertThat(product.images()).extracting(ProductImage::position).containsExactly(0, 1);
        assertThat(product.removeImage(b.id())).isEmpty();
    }

    @Test
    void givenStoredPhotosWithGaps_whenRestored_thenTheyKeepTheirOrderWithoutGaps() {
        ProductImage late = stored(7);
        ProductImage early = stored(3);

        Product restored = Product.restore(product.id(), product.details(), product.slug(), false, NOW, NOW,
            product.variants(), List.of(late, early));

        assertThat(restored.images()).extracting(ProductImage::id).containsExactly(early.id(), late.id());
        assertThat(restored.images()).extracting(ProductImage::position).containsExactly(0, 1);
    }

    private ProductImage added(Product.NewImage image) {
        return ((Product.ImageUpload.Added) product.addImage(image, NOW)).image();
    }

    private static Product.NewImage image(Optional<Color> color) {
        return new Product.NewImage(ImageId.of(UUID.randomUUID()), color, StorageKey.of(UUID.randomUUID()),
            new ImagePlaceholder("data:image/webp;base64,AAAA"), UploadKey.of(UUID.randomUUID()), FILE);
    }

    private static ProductImage stored(int position) {
        return ProductImage.restore(ImageId.of(UUID.randomUUID()), Optional.empty(), StorageKey.of(UUID.randomUUID()),
            new ImagePlaceholder("data:image/webp;base64,AAAA"), position, UploadKey.of(UUID.randomUUID()), FILE, NOW);
    }

    private static Product productWithColors(String... colors) {
        Product product = Product.create(ProductId.of(UUID.randomUUID()), new ProductDetails(
            CategoryId.of(UUID.randomUUID()), new ProductName("Pijama"), new Description(""), new Material("Algodón"),
            Season.SUMMER, Audience.WOMEN), NOW);
        var request = ((VariantMatrix.Parsed.Valid) VariantMatrix.parse(List.of("M"), List.of(colors))).request();
        product.generateVariants(request, new VariantIdentities() {
            private int next;

            @Override
            public VariantId nextId() {
                return VariantId.of(UUID.randomUUID());
            }

            @Override
            public Sku nextSku() {
                return new Sku("PP-TEST" + (++next));
            }
        }, NOW);
        return product;
    }
}
