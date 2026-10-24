package com.pijamaparty.store.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.application.ActivateCategory;
import com.pijamaparty.store.catalog.application.CreateCategory;
import com.pijamaparty.store.catalog.application.CreateProduct;
import com.pijamaparty.store.catalog.application.DeactivateCategory;
import com.pijamaparty.store.catalog.application.DeleteCategory;
import com.pijamaparty.store.catalog.application.DeleteProductImage;
import com.pijamaparty.store.catalog.application.DeleteSizeGuide;
import com.pijamaparty.store.catalog.application.EditSizeGuide;
import com.pijamaparty.store.catalog.application.GenerateVariantMatrix;
import com.pijamaparty.store.catalog.application.HideHomeBanner;
import com.pijamaparty.store.catalog.application.HideProduct;
import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.InMemoryImageProcessor;
import com.pijamaparty.store.catalog.application.ProductDetailsInput;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.application.PublishHomeBanner;
import com.pijamaparty.store.catalog.application.PublishProduct;
import com.pijamaparty.store.catalog.application.RenameCategory;
import com.pijamaparty.store.catalog.application.ReorderCategories;
import com.pijamaparty.store.catalog.application.ReorderProductImages;
import com.pijamaparty.store.catalog.application.ReplaceHomeBannerPhoto;
import com.pijamaparty.store.catalog.application.TestImages;
import com.pijamaparty.store.catalog.application.UpdateHomeBanner;
import com.pijamaparty.store.catalog.application.UpdateProduct;
import com.pijamaparty.store.catalog.application.UpdateVariant;
import com.pijamaparty.store.catalog.application.UploadProductImage;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-37: every recorded action, run on PostgreSQL next to an order whose buyer data is known, stores none of that data
 * in its subject label or its changes. Each task that records new actions adds them here (T-E8-02: catalog and home
 * page; T-E8-03: the rest), so {@link #NOT_RECORDED_YET} only shrinks.
 */
@ApiTest
@Import(ActivityPersonalDataTest.FakeImageProcessor.class)
@TestPropertySource(properties = "store.images.uploads-dir=${java.io.tmpdir}/pijamaparty-test-activity-uploads")
class ActivityPersonalDataTest {

    /** The buyer data of {@link OrderRows}: names, email, phone, national ID and address. */
    private static final List<String> PERSONAL_DATA = List.of("Ana", "Pérez", "ana@example.com", "3425551234",
        "12345678", "San Martín");

    /** Recorded by tasks still to come; each removes its actions as it records them. */
    private static final Set<ActivityAction> NOT_RECORDED_YET = EnumSet.of(
        ActivityAction.STOCK_ADJUSTED, ActivityAction.ORDER_STATUS_CHANGED, ActivityAction.TRACKING_NUMBER_CORRECTED,
        ActivityAction.WITHDRAWAL_RESOLVED, ActivityAction.COUPON_CREATED, ActivityAction.COUPON_UPDATED,
        ActivityAction.COUPON_ACTIVATED, ActivityAction.COUPON_DEACTIVATED, ActivityAction.COUPON_DELETED,
        ActivityAction.REVIEW_APPROVED, ActivityAction.REVIEW_REJECTED, ActivityAction.REVIEW_DELETED,
        ActivityAction.SHIPPING_ZONES_CHANGED, ActivityAction.FREE_SHIPPING_THRESHOLD_CHANGED,
        ActivityAction.STORE_SETTINGS_CHANGED, ActivityAction.CUSTOMER_VIEWED, ActivityAction.ADMIN_SIGNED_IN,
        ActivityAction.SECOND_FACTOR_ENROLLED, ActivityAction.RECOVERY_CODE_USED);

    private static final Actor ADMIN = RlsPolicyTester.ADMIN;
    private static final byte[] PHOTO = TestImages.png(1600, 1200);

    /** The free-text notes and upload keys the actions send, which no entry may carry either. */
    private final List<String> neverRecorded = new ArrayList<>();
    private final Catalog catalog;
    private final JdbcTemplate owner;
    private final Path uploads;

    ActivityPersonalDataTest(Catalog catalog, PostgreSQLContainer<?> postgres,
                             @Value("${store.images.uploads-dir}") Path uploads) {
        this.catalog = catalog;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.uploads = uploads;
    }

    @AfterEach
    void cleanUp() throws IOException {
        owner.execute("""
            truncate activity_entry, product_image, variant, product, size_guide, category, stock_movement,
                     stock_level, withdrawal_request, order_tracking_token, order_item, orders
            """);
        owner.update("""
            update home_banner
            set title = null, body = null, link_text = null, link_path = null, image_storage_key = null,
                image_placeholder = null, image_upload_key = null, image_content_sha256 = null, published = false
            """);
        FileSystemUtils.deleteRecursively(uploads);
    }

    @Test
    void givenAnOrderWithKnownBuyerData_whenEveryRecordedActionRuns_thenNoEntryStoresAnyOfIt() {
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, Instant.now().plusSeconds(600));

        as(this::runCatalogActions);

        List<String> forbidden = new ArrayList<>(PERSONAL_DATA);
        forbidden.addAll(neverRecorded);
        forbidden.addAll(owner.queryForList("select storage_key from product_image", String.class));
        forbidden.addAll(owner.queryForList("select image_storage_key from home_banner", String.class));
        List<Map<String, Object>> entries = owner.queryForList(
            "select action, coalesce(subject_label, '') as label, changes::text as changes from activity_entry");
        Set<ActivityAction> recorded = entries.stream()
            .map(entry -> ActivityAction.valueOf((String) entry.get("action")))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ActivityAction.class)));
        assertThat(EnumSet.complementOf(EnumSet.copyOf(recorded))).as("actions not covered by this test")
            .containsExactlyInAnyOrderElementsOf(NOT_RECORDED_YET);
        for (Map<String, Object> entry : entries) {
            String stored = entry.get("label") + " " + entry.get("changes");
            assertThat(forbidden).as("personal data, a note or a file key in %s: %s", entry.get("action"), stored)
                .noneMatch(stored::contains);
        }
    }

    /** UC-05, UC-06, RNF-31.5 and UC-32: each catalog and home page change the back office makes. */
    private void runCatalogActions() {
        CategoryId summer = ((CreateCategory.Result.Created) catalog.createCategory.execute(
            new CreateCategory.Command(ADMIN, "Verano"))).category().id();
        CategoryId winter = ((CreateCategory.Result.Created) catalog.createCategory.execute(
            new CreateCategory.Command(ADMIN, "Invierno"))).category().id();
        catalog.renameCategory.execute(new RenameCategory.Command(ADMIN, winter, "Invierno 2027"));
        catalog.reorderCategories.execute(new ReorderCategories.Command(ADMIN, List.of(winter, summer)));
        catalog.deactivateCategory.execute(new DeactivateCategory.Command(ADMIN, winter));
        catalog.activateCategory.execute(new ActivateCategory.Command(ADMIN, winter));
        String note = "Medí sobre la ropa interior.";
        neverRecorded.add(note);
        catalog.editSizeGuide.execute(new EditSizeGuide.Command(ADMIN, winter, new SizeGuideForm(List.of("Busto"),
            List.of(new SizeGuideForm.RowForm("S", List.of("83–87"))), note)));
        catalog.deleteSizeGuide.execute(new DeleteSizeGuide.Command(ADMIN, winter));
        catalog.deleteCategory.execute(new DeleteCategory.Command(ADMIN, winter));

        ProductView product = ((CreateProduct.Result.Created) catalog.createProduct.execute(new CreateProduct.Command(
            ADMIN, details(summer, "Pijama Luna", Season.SUMMER)))).product();
        catalog.updateProduct.execute(new UpdateProduct.Command(ADMIN, product.id(),
            details(summer, "Pijama Sol", Season.WINTER)));
        ProductView.VariantView variant = ((GenerateVariantMatrix.Result.Generated) catalog.generateVariants.execute(
            new GenerateVariantMatrix.Command(ADMIN, product.id(), List.of("M"), List.of("Rosa")))).product()
            .variants().getFirst();
        catalog.updateVariant.execute(new UpdateVariant.Command(ADMIN, variant.id(), Optional.of(2_200_000L),
            Optional.empty()));
        catalog.publishProduct.execute(new PublishProduct.Command(ADMIN, product.id()));
        catalog.hideProduct.execute(new HideProduct.Command(ADMIN, product.id()));
        ImageId first = upload(product);
        ImageId second = upload(product);
        catalog.reorderImages.execute(new ReorderProductImages.Command(ADMIN, product.id(), List.of(second, first)));
        catalog.deleteImage.execute(new DeleteProductImage.Command(ADMIN, product.id(), first));

        catalog.updateBanner.execute(new UpdateHomeBanner.Command(ADMIN, "Noches de invierno", "Para dormir bien.",
            "Ver", "/categoria/verano"));
        catalog.replaceBannerPhoto.execute(new ReplaceHomeBannerPhoto.Command(ADMIN, uploadKey(), PHOTO));
        catalog.publishBanner.execute(new PublishHomeBanner.Command(ADMIN));
        catalog.hideBanner.execute(new HideHomeBanner.Command(ADMIN));
    }

    private ImageId upload(ProductView product) {
        return ((UploadProductImage.Result.Uploaded) catalog.uploadImage.execute(new UploadProductImage.Command(ADMIN,
            product.id(), uploadKey(), Optional.empty(), PHOTO))).image().id();
    }

    /** A new upload key, which no entry may carry. */
    private UploadKey uploadKey() {
        UUID key = UUID.randomUUID();
        neverRecorded.add(key.toString());
        return UploadKey.of(key);
    }

    private static ProductDetailsInput details(CategoryId category, String name, Season season) {
        return new ProductDetailsInput(category, name, "Suave y fresco.", "Algodón", season, Audience.WOMEN);
    }

    private static void as(Runnable actions) {
        CurrentActor.runAs(ADMIN, actions);
    }

    /** The catalog's inbound ports, as the back office calls them. */
    record Catalog(CreateCategory createCategory, RenameCategory renameCategory,
                   ReorderCategories reorderCategories, DeactivateCategory deactivateCategory,
                   ActivateCategory activateCategory, DeleteCategory deleteCategory, EditSizeGuide editSizeGuide,
                   DeleteSizeGuide deleteSizeGuide, CreateProduct createProduct, UpdateProduct updateProduct,
                   GenerateVariantMatrix generateVariants, UpdateVariant updateVariant,
                   PublishProduct publishProduct, HideProduct hideProduct, UploadProductImage uploadImage,
                   ReorderProductImages reorderImages, DeleteProductImage deleteImage,
                   UpdateHomeBanner updateBanner, ReplaceHomeBannerPhoto replaceBannerPhoto,
                   PublishHomeBanner publishBanner, HideHomeBanner hideBanner) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeImageProcessor {

        @Bean
        @Primary
        ImageProcessor fakeImageProcessor() {
            return new InMemoryImageProcessor();
        }

        @Bean
        Catalog catalog(CreateCategory createCategory, RenameCategory renameCategory,
                        ReorderCategories reorderCategories, DeactivateCategory deactivateCategory,
                        ActivateCategory activateCategory, DeleteCategory deleteCategory, EditSizeGuide editSizeGuide,
                        DeleteSizeGuide deleteSizeGuide, CreateProduct createProduct, UpdateProduct updateProduct,
                        GenerateVariantMatrix generateVariants, UpdateVariant updateVariant,
                        PublishProduct publishProduct, HideProduct hideProduct, UploadProductImage uploadImage,
                        ReorderProductImages reorderImages, DeleteProductImage deleteImage,
                        UpdateHomeBanner updateBanner, ReplaceHomeBannerPhoto replaceBannerPhoto,
                        PublishHomeBanner publishBanner, HideHomeBanner hideBanner) {
            return new Catalog(createCategory, renameCategory, reorderCategories, deactivateCategory,
                activateCategory, deleteCategory, editSizeGuide, deleteSizeGuide, createProduct, updateProduct,
                generateVariants, updateVariant, publishProduct, hideProduct, uploadImage, reorderImages,
                deleteImage, updateBanner, replaceBannerPhoto, publishBanner, hideBanner);
        }
    }
}
