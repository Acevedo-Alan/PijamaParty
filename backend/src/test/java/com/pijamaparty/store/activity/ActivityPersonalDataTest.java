package com.pijamaparty.store.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.application.ConfirmSecondFactorEnrollment;
import com.pijamaparty.store.accounts.application.StartSecondFactorEnrollment;
import com.pijamaparty.store.accounts.application.VerifySecondFactor;
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
import com.pijamaparty.store.inventory.application.AdjustStock;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.application.ChangeOrderStatus;
import com.pijamaparty.store.ordering.application.CorrectShipmentTrackingNumber;
import com.pijamaparty.store.ordering.application.ResolveWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import com.pijamaparty.store.promotions.application.ActivateCoupon;
import com.pijamaparty.store.promotions.application.CouponInput;
import com.pijamaparty.store.promotions.application.CreateCoupon;
import com.pijamaparty.store.promotions.application.DeactivateCoupon;
import com.pijamaparty.store.promotions.application.DeleteCoupon;
import com.pijamaparty.store.promotions.application.UpdateCoupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import com.pijamaparty.store.reviews.application.ApproveReview;
import com.pijamaparty.store.reviews.application.DeleteReview;
import com.pijamaparty.store.reviews.application.RejectReview;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.shipping.application.ReplaceShippingZoneRates;
import com.pijamaparty.store.shipping.application.SetFreeShippingThreshold;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
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
        ActivityAction.STORE_SETTINGS_CHANGED, ActivityAction.CUSTOMER_VIEWED);

    private static final Actor ADMIN = RlsPolicyTester.ADMIN;
    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    /** In the format the database requires (V6); this administrator signs in with codes only, never a password. */
    private static final String UNUSED_PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

    /** The free-text notes, upload keys, codes and secrets the actions send, which no entry may carry either. */
    private final List<String> neverRecorded = new ArrayList<>();
    private final Catalog catalog;
    private final BackOffice backOffice;
    private final JdbcTemplate owner;
    private final Path uploads;
    private ProductView product;
    private ProductView.VariantView variant;
    private boolean administratorAdded;

    ActivityPersonalDataTest(Catalog catalog, BackOffice backOffice, PostgreSQLContainer<?> postgres,
                             @Value("${store.images.uploads-dir}") Path uploads) {
        this.catalog = catalog;
        this.backOffice = backOffice;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.uploads = uploads;
    }

    @AfterEach
    void cleanUp() throws IOException {
        owner.execute("""
            truncate activity_entry, product_image, variant, product, size_guide, category, stock_movement,
                     stock_level, withdrawal_request, order_tracking_token, order_item, orders, coupon_redemption,
                     coupon_scope, coupon, review, shipping_zone_rate
            """);
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
        owner.update("""
            update home_banner
            set title = null, body = null, link_text = null, link_path = null, image_storage_key = null,
                image_placeholder = null, image_upload_key = null, image_content_sha256 = null, published = false
            """);
        owner.update("delete from admin_recovery_code");
        if (administratorAdded) {
            owner.update("delete from app_user where role = 'ADMIN'");
        } else {
            owner.update("""
                update app_user set failed_login_attempts = 0, locked_until = null, totp_secret = null,
                                    totp_enabled_at = null, totp_last_step = null
                where role = 'ADMIN'
                """);
        }
        FileSystemUtils.deleteRecursively(uploads);
    }

    @Test
    void givenAnOrderWithKnownBuyerData_whenEveryRecordedActionRuns_thenNoEntryStoresAnyOfIt() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, Instant.now().plusSeconds(600));

        as(this::runCatalogActions);
        as(this::runStockActions);
        as(() -> runOrderActions(order));
        as(this::runCouponActions);
        as(this::runReviewActions);
        as(this::runShippingActions);
        runSignInActions();

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
            assertThat(forbidden).as("personal data, a note, a code or a file key in %s: %s", entry.get("action"),
                stored).noneMatch(stored::contains);
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

        product = ((CreateProduct.Result.Created) catalog.createProduct.execute(new CreateProduct.Command(
            ADMIN, details(summer, "Pijama Luna", Season.SUMMER)))).product();
        catalog.updateProduct.execute(new UpdateProduct.Command(ADMIN, product.id(),
            details(summer, "Pijama Sol", Season.WINTER)));
        variant = ((GenerateVariantMatrix.Result.Generated) catalog.generateVariants.execute(
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

    /** UC-07: an adjustment whose note names the buyer. */
    private void runStockActions() {
        String note = "Devolución de Ana Pérez";
        neverRecorded.add(note);
        backOffice.adjustStock.execute(new AdjustStock.Command(ADMIN, VariantId.of(variant.id().value()),
            AdjustmentKey.of(UUID.randomUUID()), 5, Optional.of(note)));
    }

    /** UC-18 and UC-29: the order of the buyer through its states, another one cancelled, and a withdrawal. */
    private void runOrderActions(OrderId order) {
        backOffice.changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PAID,
            OrderStatus.PREPARING, Optional.empty(), Optional.empty()));
        backOffice.changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PREPARING,
            OrderStatus.SHIPPED, Optional.of("AR000111222"), Optional.empty()));
        backOffice.correctTrackingNumber.execute(new CorrectShipmentTrackingNumber.Command(ADMIN, order,
            "AR000111333"));
        OrderId cancelled = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, cancelled, OrderStatus.PAID, Instant.now().plusSeconds(600));
        backOffice.changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, cancelled, OrderStatus.PAID,
            OrderStatus.CANCELLED, Optional.empty(), Optional.of(false)));

        UUID withdrawal = UUID.randomUUID();
        owner.update("""
            insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at)
            values (?, ?, 'ARR-7K2M9QXZ', 'RECEIVED', false, now() - interval '1 minute')
            """, withdrawal, order.value());
        backOffice.resolveWithdrawal.execute(new ResolveWithdrawal.Command(ADMIN, withdrawal));
    }

    /** UC-11 and UC-23: a coupon through every change, and another one deleted. */
    private void runCouponActions() {
        CouponId coupon = created(coupon("VERANO10", 10));
        backOffice.updateCoupon.execute(new UpdateCoupon.Command(ADMIN, coupon, new CouponInput("VERANO15",
            CouponType.FIXED_AMOUNT, 500_000, Optional.of(LocalDate.of(2026, 12, 1)),
            Optional.of(LocalDate.of(2027, 2, 28)), Optional.of(100), Optional.of(2_000_000L))));
        backOffice.deactivateCoupon.execute(new DeactivateCoupon.Command(ADMIN, coupon));
        backOffice.activateCoupon.execute(new ActivateCoupon.Command(ADMIN, coupon));
        backOffice.deleteCoupon.execute(new DeleteCoupon.Command(ADMIN, created(coupon("INVIERNO", 5))));
    }

    /** UC-25: a review by the buyer, whose name and text no entry may carry. */
    private void runReviewActions() {
        String text = "Muy cómodo, lo recomienda Ana";
        neverRecorded.add(text);
        ReviewId review = ReviewId.of(UUID.randomUUID());
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at)
            values (?, ?, ?, 5, ?, 'Ana', 'PENDING', now(), now())
            """, review.value(), product.id().value(), UUID.randomUUID(), text);
        backOffice.approveReview.execute(new ApproveReview.Command(ADMIN, review));
        backOffice.rejectReview.execute(new RejectReview.Command(ADMIN, review));
        backOffice.deleteReview.execute(new DeleteReview.Command(ADMIN, review));
    }

    /** UC-12: the costs of two provinces and the free-shipping threshold. */
    private void runShippingActions() {
        backOffice.replaceZones.execute(new ReplaceShippingZoneRates.Command(ADMIN, List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, 500_000),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 650_000))));
        backOffice.setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(5_000_000L)));
    }

    /**
     * UC-04: the administrator enrolls its authenticator app, which signs it in, then signs in with a recovery code;
     * as the sign-in API does, before there is an administrator actor.
     */
    private void runSignInActions() {
        UserId administrator = administrator();
        var started = (StartSecondFactorEnrollment.Result.Started) backOffice.startEnrollment.execute(
            new StartSecondFactorEnrollment.Command(administrator));
        neverRecorded.add(started.secretText());
        var enrolled = (ConfirmSecondFactorEnrollment.Result.Enrolled) backOffice.confirmEnrollment.execute(
            new ConfirmSecondFactorEnrollment.Command(administrator, started.secret(), appCode(started.secretText())));
        neverRecorded.addAll(enrolled.recoveryCodes());
        backOffice.verifySecondFactor.execute(new VerifySecondFactor.Command(administrator,
            enrolled.recoveryCodes().getFirst()));
    }

    /** The store's administrator, added when no other test left one; its email and name are personal data too. */
    private UserId administrator() {
        List<UUID> existing = owner.queryForList("select id from app_user where role = 'ADMIN'", UUID.class);
        if (existing.isEmpty()) {
            owner.update("""
                insert into app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                                      created_at, updated_at)
                values (?, 'duena@example.com', true, ?, 'Dueña', 'ADMIN', 0, now(), now())
                """, RlsPolicyTester.ADMIN_ID.value(), UNUSED_PASSWORD_HASH);
            administratorAdded = true;
        }
        neverRecorded.addAll(owner.queryForList("select email from app_user where role = 'ADMIN'", String.class));
        neverRecorded.addAll(owner.queryForList("select name from app_user where role = 'ADMIN'", String.class));
        return UserId.of(owner.queryForObject("select id from app_user where role = 'ADMIN'", UUID.class));
    }

    /** What the authenticator app shows now. */
    private static String appCode(String secret) {
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret,
                Instant.now().getEpochSecond() / 30);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CouponId created(CouponInput coupon) {
        return ((CreateCoupon.Result.Created) backOffice.createCoupon.execute(new CreateCoupon.Command(ADMIN,
            coupon))).coupon().id();
    }

    private static CouponInput coupon(String code, long percent) {
        return new CouponInput(code, CouponType.PERCENTAGE, percent, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());
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

    /** The inbound ports of the other modules' back-office changes, and of the administrator's sign-in. */
    record BackOffice(AdjustStock adjustStock, ChangeOrderStatus changeStatus,
                      CorrectShipmentTrackingNumber correctTrackingNumber, ResolveWithdrawal resolveWithdrawal,
                      CreateCoupon createCoupon, UpdateCoupon updateCoupon, ActivateCoupon activateCoupon,
                      DeactivateCoupon deactivateCoupon, DeleteCoupon deleteCoupon, ApproveReview approveReview,
                      RejectReview rejectReview, DeleteReview deleteReview, ReplaceShippingZoneRates replaceZones,
                      SetFreeShippingThreshold setThreshold, StartSecondFactorEnrollment startEnrollment,
                      ConfirmSecondFactorEnrollment confirmEnrollment, VerifySecondFactor verifySecondFactor) {
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

        @Bean
        BackOffice backOffice(AdjustStock adjustStock, ChangeOrderStatus changeStatus,
                              CorrectShipmentTrackingNumber correctTrackingNumber,
                              ResolveWithdrawal resolveWithdrawal, CreateCoupon createCoupon,
                              UpdateCoupon updateCoupon, ActivateCoupon activateCoupon,
                              DeactivateCoupon deactivateCoupon, DeleteCoupon deleteCoupon,
                              ApproveReview approveReview, RejectReview rejectReview, DeleteReview deleteReview,
                              ReplaceShippingZoneRates replaceZones, SetFreeShippingThreshold setThreshold,
                              StartSecondFactorEnrollment startEnrollment,
                              ConfirmSecondFactorEnrollment confirmEnrollment,
                              VerifySecondFactor verifySecondFactor) {
            return new BackOffice(adjustStock, changeStatus, correctTrackingNumber, resolveWithdrawal, createCoupon,
                updateCoupon, activateCoupon, deactivateCoupon, deleteCoupon, approveReview, rejectReview,
                deleteReview, replaceZones, setThreshold, startEnrollment, confirmEnrollment, verifySecondFactor);
        }
    }
}
