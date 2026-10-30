package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.UploadKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** UC-06: uploading, reordering and deleting the photos of a product. */
class ProductImageServicesTest {

    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    private static final byte[] OTHER_PHOTO = TestImages.png(800, 800);

    private final ProductFixture fixture = new ProductFixture();
    private final ProductView product = fixture.withMatrix("Pijama", List.of("M"), List.of("Rosa", "Celeste"));

    @Test
    void givenAPhoto_whenUploaded_thenItsRenditionsAndPlaceholderAreStoredAndItGoesLast() {
        ProductView.ImageView first = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO);

        UploadProductImage.Result result = upload(UUID.randomUUID(), Optional.of("  Rosa "), OTHER_PHOTO);

        assertThat(result).isInstanceOfSatisfying(UploadProductImage.Result.Uploaded.class, uploaded -> {
            assertThat(uploaded.image().color()).contains("Rosa");
            assertThat(uploaded.image().placeholder()).startsWith("data:image/webp;base64,");
            assertThat(uploaded.image().files()).extracting(ProductView.ImageFile::width)
                .containsExactly(400, 600, 800, 1200);
            assertThat(uploaded.image().files()).allSatisfy(file ->
                assertThat(fixture.imageStore.file(file.fileName())).isPresent());
        });
        assertThat(images()).extracting(ProductView.ImageView::id).first().isEqualTo(first.id());
        assertThat(images()).hasSize(2);
        assertThat(first.color()).isEmpty();
        // Two photos, four files each (UC-06).
        assertThat(fixture.imageStore.fileNames()).hasSize(8);
    }

    @Test
    void givenAnUploadCutAndRetried_whenTheSameKeyAndFileArrive_thenTheStoredPhotoIsReturnedWithoutDuplicates() {
        UUID key = UUID.randomUUID();
        ProductView.ImageView first = uploaded(key, Optional.empty(), PHOTO);

        UploadProductImage.Result retry = upload(key, Optional.empty(), PHOTO);

        assertThat(retry).isEqualTo(new UploadProductImage.Result.AlreadyUploaded(first));
        assertThat(images()).hasSize(1);
        assertThat(fixture.imageStore.fileNames()).hasSize(4);
        assertThat(fixture.imageProcessor.conversions()).isEqualTo(1);
    }

    @Test
    void givenAKeyUsedWithAnotherFile_whenUploaded_thenItIsRejected() {
        UUID key = UUID.randomUUID();
        uploaded(key, Optional.empty(), PHOTO);

        assertThat(upload(key, Optional.empty(), OTHER_PHOTO)).isEqualTo(new UploadProductImage.Result.KeyReused());
        assertThat(images()).hasSize(1);
    }

    @Test
    void givenAFileThatIsNotAPhoto_whenUploaded_thenItIsRejectedWithoutConvertingIt() {
        byte[] text = "not a photo, despite its .jpg name".getBytes(StandardCharsets.UTF_8);

        assertThat(upload(UUID.randomUUID(), Optional.empty(), text))
            .isEqualTo(new UploadProductImage.Result.UnsupportedFormat());
        assertThat(fixture.imageProcessor.conversions()).isZero();
        assertThat(images()).isEmpty();
    }

    @Test
    void givenPhotosTheProcessorRefuses_whenUploaded_thenNothingIsStored() {
        assertThat(upload(UUID.randomUUID(), Optional.empty(), TestImages.pngHeaderOnly(20_000, 20)))
            .isEqualTo(new UploadProductImage.Result.TooLarge());
        byte[] broken = PHOTO.clone();
        System.arraycopy(new byte[64], 0, broken, 12, 64);
        assertThat(upload(UUID.randomUUID(), Optional.empty(), broken))
            .isEqualTo(new UploadProductImage.Result.Unreadable());

        assertThat(fixture.imageStore.fileNames()).isEmpty();
        assertThat(images()).isEmpty();
    }

    @Test
    void givenAProductWith8Photos_whenAnotherIsUploaded_thenItIsRejected() {
        UUID firstKey = UUID.randomUUID();
        uploaded(firstKey, Optional.empty(), PHOTO);
        for (int i = 1; i < Product.MAX_IMAGES; i++) {
            uploaded(UUID.randomUUID(), Optional.empty(), PHOTO);
        }

        assertThat(upload(UUID.randomUUID(), Optional.empty(), PHOTO))
            .isEqualTo(new UploadProductImage.Result.TooManyImages());
        assertThat(upload(firstKey, Optional.empty(), PHOTO))
            .isInstanceOf(UploadProductImage.Result.AlreadyUploaded.class);
        assertThat(images()).hasSize(Product.MAX_IMAGES);
        assertThat(fixture.imageProcessor.conversions()).isEqualTo(Product.MAX_IMAGES);
    }

    @Test
    void givenAColorTheProductDoesNotHave_whenUploaded_thenItIsRejected() {
        assertThat(upload(UUID.randomUUID(), Optional.of("Verde"), PHOTO))
            .isEqualTo(new UploadProductImage.Result.UnknownColor());
        assertThat(upload(UUID.randomUUID(), Optional.of("x".repeat(31)), PHOTO))
            .isEqualTo(new UploadProductImage.Result.UnknownColor());
        assertThat(upload(UUID.randomUUID(), Optional.of(" "), PHOTO))
            .isEqualTo(new UploadProductImage.Result.UnknownColor());
        assertThat(fixture.imageProcessor.conversions()).isZero();
    }

    @Test
    void givenTwoUploadsWithTheSameKeyAtOnce_whenTheSecondFinishesLast_thenItAnswersTheFirstAndRemovesItsFiles() {
        UUID key = UUID.randomUUID();
        AtomicReference<UploadProductImage.Result> inner = new AtomicReference<>();
        ImageProcessor racing = content -> {
            // While this upload converts, another one with the same key completes.
            inner.set(upload(key, Optional.empty(), PHOTO));
            return fixture.imageProcessor.process(content);
        };
        var outer = new UploadProductImageService(fixture.imageTransactions, racing, fixture.imageStore, fixture.ids);

        UploadProductImage.Result result = outer.execute(new UploadProductImage.Command(ADMIN, product.id(),
            UploadKey.of(key), Optional.empty(), PHOTO));

        var first = (UploadProductImage.Result.Uploaded) inner.get();
        assertThat(result).isEqualTo(new UploadProductImage.Result.AlreadyUploaded(first.image()));
        assertThat(images()).hasSize(1);
        assertThat(fixture.imageStore.fileNames()).containsExactlyInAnyOrderElementsOf(
            first.image().files().stream().map(ProductView.ImageFile::fileName).toList());
    }

    @Test
    void givenAMissingProductOrAnotherActor_whenUploading_thenItIsRejected() {
        assertThat(fixture.uploadImage.execute(new UploadProductImage.Command(ADMIN, ProductId.of(UUID.randomUUID()),
            UploadKey.of(UUID.randomUUID()), Optional.empty(), PHOTO)))
            .isEqualTo(new UploadProductImage.Result.NotFound());
        assertThat(fixture.uploadImage.execute(new UploadProductImage.Command(CUSTOMER, product.id(),
            UploadKey.of(UUID.randomUUID()), Optional.empty(), PHOTO)))
            .isEqualTo(new UploadProductImage.Result.Forbidden());
        assertThat(fixture.imageProcessor.conversions()).isZero();
    }

    @Test
    void givenThreePhotos_whenReordered_thenTheProductShowsTheNewOrder() {
        ImageId a = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();
        ImageId b = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();
        ImageId c = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();

        ReorderProductImages.Result result =
            fixture.reorderImages.execute(new ReorderProductImages.Command(ADMIN, product.id(), List.of(b, c, a)));

        assertThat(result).isInstanceOfSatisfying(ReorderProductImages.Result.Reordered.class, reordered ->
            assertThat(reordered.product().images()).extracting(ProductView.ImageView::id).containsExactly(b, c, a));
        assertThat(images()).extracting(ProductView.ImageView::id).containsExactly(b, c, a);
    }

    @Test
    void givenAnIncompleteOrderAMissingProductOrAnotherActor_whenReordering_thenItIsRejected() {
        ImageId a = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();
        uploaded(UUID.randomUUID(), Optional.empty(), PHOTO);

        assertThat(fixture.reorderImages.execute(new ReorderProductImages.Command(ADMIN, product.id(), List.of(a))))
            .isEqualTo(new ReorderProductImages.Result.InvalidOrder());
        assertThat(fixture.reorderImages.execute(new ReorderProductImages.Command(ADMIN,
            ProductId.of(UUID.randomUUID()), List.of()))).isEqualTo(new ReorderProductImages.Result.NotFound());
        assertThat(fixture.reorderImages.execute(new ReorderProductImages.Command(CUSTOMER, product.id(), List.of())))
            .isEqualTo(new ReorderProductImages.Result.Forbidden());
    }

    @Test
    void givenAPhoto_whenDeleted_thenItLeavesTheProductAndItsFilesAreRemoved() {
        ImageId a = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();
        ProductView.ImageView b = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO);
        ImageId c = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();

        assertThat(fixture.deleteImage.execute(new DeleteProductImage.Command(ADMIN, product.id(), b.id())))
            .isEqualTo(new DeleteProductImage.Result.Deleted());

        assertThat(images()).extracting(ProductView.ImageView::id).containsExactly(a, c);
        assertThat(b.files()).allSatisfy(file -> assertThat(fixture.imageStore.file(file.fileName())).isEmpty());
        assertThat(fixture.imageStore.fileNames()).hasSize(8);
    }

    @Test
    void givenAnUnknownPhotoOrProductOrAnotherActor_whenDeleting_thenItIsRejected() {
        ImageId image = uploaded(UUID.randomUUID(), Optional.empty(), PHOTO).id();
        ProductView other = fixture.created("Camisón");

        assertThat(fixture.deleteImage.execute(new DeleteProductImage.Command(ADMIN, other.id(), image)))
            .isEqualTo(new DeleteProductImage.Result.NotFound());
        assertThat(fixture.deleteImage.execute(new DeleteProductImage.Command(ADMIN, ProductId.of(UUID.randomUUID()),
            image))).isEqualTo(new DeleteProductImage.Result.NotFound());
        assertThat(fixture.deleteImage.execute(new DeleteProductImage.Command(CUSTOMER, product.id(), image)))
            .isEqualTo(new DeleteProductImage.Result.Forbidden());
        assertThat(images()).hasSize(1);
    }

    private UploadProductImage.Result upload(UUID key, Optional<String> color, byte[] content) {
        return fixture.uploadImage.execute(new UploadProductImage.Command(ADMIN, product.id(), UploadKey.of(key),
            color, content));
    }

    private ProductView.ImageView uploaded(UUID key, Optional<String> color, byte[] content) {
        return ((UploadProductImage.Result.Uploaded) upload(key, color, content)).image();
    }

    private List<ProductView.ImageView> images() {
        return ((GetProductForAdmin.Result.Found) fixture.get.execute(new GetProductForAdmin.Query(ADMIN,
            product.id()))).product().images();
    }
}
