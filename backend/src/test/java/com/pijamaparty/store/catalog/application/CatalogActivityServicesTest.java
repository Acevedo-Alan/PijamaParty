package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CATEGORY;
import static com.pijamaparty.store.catalog.application.ProductFixture.details;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.catalog.domain.SizeGuideForm.RowForm;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * UC-37 (T-E8-02): each catalog and home page use case records what it changed, and nothing when it changed nothing
 * or was refused.
 */
class CatalogActivityServicesTest {

    private static final byte[] PHOTO = TestImages.png(1600, 1200);

    private final ProductFixture fixture = new ProductFixture();
    private final InMemoryCatalogActivity activity = fixture.activity;

    private static ActivitySubject subject(SubjectType type, UUID id, String label) {
        return ActivitySubject.of(type, id, label);
    }

    private static ActivityEntry entry(ActivityAction action, ActivitySubject subject, FieldChange... changes) {
        return new ActivityEntry(ADMIN.userId().orElseThrow(), action, subject, List.of(changes));
    }

    @Nested
    class Categories {

        private final CreateCategory create = new CreateCategoryService(fixture.categories, fixture.categories,
            activity, new InMemoryIdGenerator(), fixture.clock);
        private final RenameCategory rename = new RenameCategoryService(fixture.categories, fixture.categories,
            fixture.events, activity, fixture.clock);
        private final ReorderCategories reorder = new ReorderCategoriesService(fixture.categories,
            fixture.categories, activity, fixture.clock);
        private final DeactivateCategory deactivate = new DeactivateCategoryService(fixture.categories,
            fixture.categories, activity, fixture.clock);
        private final ActivateCategory activate = new ActivateCategoryService(fixture.categories,
            fixture.categories, activity, fixture.clock);
        private final DeleteCategory delete = new DeleteCategoryService(fixture.categories, fixture.categories,
            activity);

        private CategoryView created(String name) {
            return ((CreateCategory.Result.Created) create.execute(new CreateCategory.Command(ADMIN, name)))
                .category();
        }

        @Test
        void givenANewCategory_whenCreated_thenItsNameIsRecorded() {
            CategoryView winter = created("Invierno");

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.CATEGORY_CREATED,
                subject(SubjectType.CATEGORY, winter.id().value(), "Invierno"),
                FieldChange.of("name", null, "Invierno")));
        }

        @Test
        void givenACategory_whenRenamed_thenTheOldAndNewNamesAreRecorded_andRenamingToTheSameNameRecordsNothing() {
            rename.execute(new RenameCategory.Command(ADMIN, CATEGORY, "Verano 2027"));
            rename.execute(new RenameCategory.Command(ADMIN, CATEGORY, "Verano 2027"));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.CATEGORY_RENAMED,
                subject(SubjectType.CATEGORY, CATEGORY.value(), "Verano 2027"),
                FieldChange.of("name", "Verano", "Verano 2027")));
        }

        @Test
        void givenARenameRefused_whenItEnds_thenNothingIsRecorded() {
            CategoryView winter = created("Invierno");
            activity.clear();

            rename.execute(new RenameCategory.Command(ADMIN, winter.id(), "verano"));
            rename.execute(new RenameCategory.Command(ADMIN, winter.id(), "  "));

            assertThat(activity.entries()).isEmpty();
        }

        @Test
        void givenTwoCategories_whenReordered_thenTheOrderBeforeAndAfterIsRecorded_andTheSameOrderRecordsNothing() {
            CategoryView winter = created("Invierno");
            activity.clear();

            reorder.execute(new ReorderCategories.Command(ADMIN, List.of(winter.id(), CATEGORY)));
            reorder.execute(new ReorderCategories.Command(ADMIN, List.of(winter.id(), CATEGORY)));
            reorder.execute(new ReorderCategories.Command(ADMIN, List.of(winter.id())));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.CATEGORIES_REORDERED,
                ActivitySubject.of(SubjectType.CATEGORY), FieldChange.of("order", "Verano, Invierno",
                    "Invierno, Verano")));
        }

        @Test
        void givenAnActiveCategory_whenDeactivatedAndActivatedTwice_thenEachChangeIsRecordedOnce() {
            ActivitySubject summer = subject(SubjectType.CATEGORY, CATEGORY.value(), "Verano");

            deactivate.execute(new DeactivateCategory.Command(ADMIN, CATEGORY));
            deactivate.execute(new DeactivateCategory.Command(ADMIN, CATEGORY));
            activate.execute(new ActivateCategory.Command(ADMIN, CATEGORY));
            activate.execute(new ActivateCategory.Command(ADMIN, CATEGORY));

            assertThat(activity.entries()).containsExactly(entry(ActivityAction.CATEGORY_DEACTIVATED, summer),
                entry(ActivityAction.CATEGORY_ACTIVATED, summer));
        }

        @Test
        void givenACategory_whenDeleted_thenItsNameIsRecorded_andACategoryWithProductsRecordsNothing() {
            CategoryView winter = created("Invierno");
            fixture.categories.addProductTo(CATEGORY);
            activity.clear();

            delete.execute(new DeleteCategory.Command(ADMIN, CATEGORY));
            delete.execute(new DeleteCategory.Command(ADMIN, winter.id()));
            delete.execute(new DeleteCategory.Command(ADMIN, CategoryId.of(UUID.randomUUID())));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.CATEGORY_DELETED,
                subject(SubjectType.CATEGORY, winter.id().value(), "Invierno")));
        }
    }

    @Nested
    class Products {

        @Test
        void givenANewProduct_whenCreated_thenItIsRecordedWithItsName() {
            ProductView product = fixture.created("Pijama Luna");

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.PRODUCT_CREATED,
                subject(SubjectType.PRODUCT, product.id().value(), "Pijama Luna")));
        }

        @Test
        void givenAProduct_whenSomeDetailsChange_thenOnlyThoseAreRecordedBeforeAndAfter() {
            ProductView product = fixture.created("Pijama Luna");
            CategoryId winter = CategoryId.of(UUID.randomUUID());
            fixture.categories.save(Category.create(winter, new CategoryName("Invierno"),
                fixture.categories.findAll(), ProductFixture.NOW));
            activity.clear();

            fixture.update.execute(new UpdateProduct.Command(ADMIN, product.id(), new ProductDetailsInput(winter,
                "Pijama Sol", "Suave y fresco.", "Algodón", Season.WINTER, Audience.WOMEN)));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.PRODUCT_UPDATED,
                subject(SubjectType.PRODUCT, product.id().value(), "Pijama Sol"),
                FieldChange.of("name", "Pijama Luna", "Pijama Sol"),
                FieldChange.of("category", "Verano", "Invierno"),
                FieldChange.of("season", "SUMMER", "WINTER")));
        }

        @Test
        void givenAProduct_whenSavedWithTheSameDetailsOrInvalidOnes_thenNothingIsRecorded() {
            ProductView product = fixture.created("Pijama Luna");
            activity.clear();

            fixture.update.execute(new UpdateProduct.Command(ADMIN, product.id(), details("Pijama Luna")));
            fixture.update.execute(new UpdateProduct.Command(ADMIN, product.id(), details("")));

            assertThat(activity.entries()).isEmpty();
        }

        @Test
        void givenAProduct_whenVariantsAreGenerated_thenTheirCountSizesAndColorsAreRecorded() {
            ProductView product = fixture.withMatrix("Pijama Luna", List.of("S", "M"), List.of("Rosa"));
            activity.clear();

            fixture.generate.execute(new GenerateVariantMatrix.Command(ADMIN, product.id(), List.of("L"),
                List.of("Rosa", "Azul")));
            fixture.generate.execute(new GenerateVariantMatrix.Command(ADMIN, product.id(), List.of("S"),
                List.of("Rosa")));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.VARIANTS_GENERATED,
                subject(SubjectType.PRODUCT, product.id().value(), "Pijama Luna"),
                FieldChange.of("variants", null, "2"), FieldChange.of("sizes", null, "L"),
                FieldChange.of("colors", null, "Rosa, Azul")));
        }

        @Test
        void givenAVariantPriceChange_whenSaved_thenTheSkuAndBothPricesAreRecorded() {
            ProductView.VariantView variant = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"))
                .variants().getFirst();
            fixture.priced(variant, 2_000_000);
            activity.clear();

            fixture.priced(variant, 2_200_000);
            fixture.priced(variant, 2_200_000);

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.VARIANT_UPDATED,
                subject(SubjectType.VARIANT, variant.id().value(), variant.sku()),
                FieldChange.of("price", "$20.000", "$22.000")));
        }

        @Test
        void givenAVariant_whenDeactivated_thenItsActiveStateIsRecorded_andAnInvalidPriceRecordsNothing() {
            ProductView.VariantView variant = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"))
                .variants().getFirst();
            activity.clear();

            fixture.updateVariant.execute(new UpdateVariant.Command(ADMIN, variant.id(), Optional.of(0L),
                Optional.of(false)));
            fixture.updateVariant.execute(new UpdateVariant.Command(ADMIN, variant.id(), Optional.empty(),
                Optional.of(false)));

            assertThat(activity.single()).isEqualTo(entry(ActivityAction.VARIANT_UPDATED,
                subject(SubjectType.VARIANT, variant.id().value(), variant.sku()),
                FieldChange.of("active", "true", "false")));
        }

        @Test
        void givenAProduct_whenPublishedAndHiddenTwice_thenEachChangeIsRecordedOnce() {
            ProductView product = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"));
            fixture.priced(product.variants().getFirst(), 1_500_000);
            ActivitySubject luna = subject(SubjectType.PRODUCT, product.id().value(), "Pijama Luna");
            activity.clear();

            fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));
            fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));
            fixture.hide.execute(new HideProduct.Command(ADMIN, product.id()));
            fixture.hide.execute(new HideProduct.Command(ADMIN, product.id()));

            assertThat(activity.entries()).containsExactly(entry(ActivityAction.PRODUCT_PUBLISHED, luna),
                entry(ActivityAction.PRODUCT_HIDDEN, luna));
        }

        @Test
        void givenAProductWithoutAPricedVariant_whenPublishing_thenNothingIsRecorded() {
            ProductView product = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"));
            activity.clear();

            fixture.publish.execute(new PublishProduct.Command(ADMIN, product.id()));

            assertThat(activity.entries()).isEmpty();
        }

        @Test
        void givenPhotos_whenUploadedReorderedAndDeleted_thenEachIsRecordedWithTheProductOnly() {
            ProductView product = fixture.withMatrix("Pijama Luna", List.of("M"), List.of("Rosa"));
            UUID key = UUID.randomUUID();
            ImageId first = upload(product, key);
            upload(product, key);
            ImageId second = upload(product, UUID.randomUUID());
            fixture.reorderImages.execute(new ReorderProductImages.Command(ADMIN, product.id(),
                List.of(second, first)));
            fixture.reorderImages.execute(new ReorderProductImages.Command(ADMIN, product.id(),
                List.of(second, first)));
            fixture.deleteImage.execute(new DeleteProductImage.Command(ADMIN, product.id(), first));
            fixture.deleteImage.execute(new DeleteProductImage.Command(ADMIN, product.id(), first));

            ActivitySubject luna = subject(SubjectType.PRODUCT, product.id().value(), "Pijama Luna");
            assertThat(activity.entries().subList(2, activity.entries().size())).containsExactly(
                entry(ActivityAction.PRODUCT_IMAGE_UPLOADED, luna), entry(ActivityAction.PRODUCT_IMAGE_UPLOADED, luna),
                entry(ActivityAction.PRODUCT_IMAGES_REORDERED, luna), entry(ActivityAction.PRODUCT_IMAGE_DELETED, luna));
        }

        private ImageId upload(ProductView product, UUID key) {
            UploadProductImage.Result result = fixture.uploadImage.execute(new UploadProductImage.Command(ADMIN,
                product.id(), UploadKey.of(key), Optional.empty(), PHOTO));
            return switch (result) {
                case UploadProductImage.Result.Uploaded uploaded -> uploaded.image().id();
                case UploadProductImage.Result.AlreadyUploaded uploaded -> uploaded.image().id();
                default -> throw new AssertionError(result);
            };
        }
    }

    @Nested
    class SizeGuides {

        private static final SizeGuideForm GUIDE = new SizeGuideForm(List.of("Busto (cm)"),
            List.of(new RowForm("S", List.of("83–87"))), null);

        private final InMemorySizeGuideStore guides = new InMemorySizeGuideStore(fixture.categories);
        private final EditSizeGuide edit = new EditSizeGuideService(fixture.categories, guides, guides, activity,
            fixture.clock);
        private final DeleteSizeGuide delete = new DeleteSizeGuideService(fixture.categories, guides, guides,
            activity);

        @Test
        void givenAGuide_whenSavedTwiceAndDeletedTwice_thenTheChangesAreRecordedOnce() {
            ActivitySubject summer = subject(SubjectType.CATEGORY, CATEGORY.value(), "Verano");

            edit.execute(new EditSizeGuide.Command(ADMIN, CATEGORY, GUIDE));
            edit.execute(new EditSizeGuide.Command(ADMIN, CATEGORY, GUIDE));
            edit.execute(new EditSizeGuide.Command(ADMIN, CATEGORY, new SizeGuideForm(List.of(), List.of(), null)));
            delete.execute(new DeleteSizeGuide.Command(ADMIN, CATEGORY));
            delete.execute(new DeleteSizeGuide.Command(ADMIN, CATEGORY));

            assertThat(activity.entries()).containsExactly(entry(ActivityAction.SIZE_GUIDE_SAVED, summer),
                entry(ActivityAction.SIZE_GUIDE_DELETED, summer));
        }
    }

    @Nested
    class HomeBanner {

        private final InMemoryHomeBannerStore store = new InMemoryHomeBannerStore();
        private final UpdateHomeBanner update = new UpdateHomeBannerService(store, store, activity, fixture.clock);
        private final PublishHomeBanner publish = new PublishHomeBannerService(store, store, activity,
            fixture.clock);
        private final HideHomeBanner hide = new HideHomeBannerService(store, store, activity, fixture.clock);
        private final ReplaceHomeBannerPhoto replacePhoto = new ReplaceHomeBannerPhotoService(
            new HomeBannerPhotoTransactions(store, store, activity, fixture.clock), new InMemoryImageProcessor(),
            new InMemoryImageStore(), new InMemoryIdGenerator());

        @Test
        void givenTheBanner_whenItsTextsAreEdited_thenOnlyTheChangedOnesAreRecorded() {
            update.execute(new UpdateHomeBanner.Command(ADMIN, "Kigurumis", "", "Ver", "/categoria/kigurumis"));
            update.execute(new UpdateHomeBanner.Command(ADMIN, "Kigurumis", "", "Ver", "/categoria/kigurumis"));
            update.execute(new UpdateHomeBanner.Command(ADMIN, "Kigurumis de invierno", "", "Ver",
                "/categoria/kigurumis"));
            update.execute(new UpdateHomeBanner.Command(ADMIN, "", "", "Ver", "/"));

            ActivitySubject banner = ActivitySubject.of(SubjectType.HOME_BANNER);
            assertThat(activity.entries()).containsExactly(
                entry(ActivityAction.HOME_BANNER_UPDATED, banner, FieldChange.of("title", null, "Kigurumis"),
                    FieldChange.of("linkText", null, "Ver"),
                    FieldChange.of("linkPath", null, "/categoria/kigurumis")),
                entry(ActivityAction.HOME_BANNER_UPDATED, banner,
                    FieldChange.of("title", "Kigurumis", "Kigurumis de invierno")));
        }

        @Test
        void givenTheBanner_whenItsPhotoIsReplacedAndItIsPublishedAndHiddenTwice_thenEachChangeIsRecordedOnce() {
            update.execute(new UpdateHomeBanner.Command(ADMIN, "Kigurumis", "", "Ver", "/"));
            publish.execute(new PublishHomeBanner.Command(ADMIN));
            UUID key = UUID.randomUUID();
            replacePhoto.execute(new ReplaceHomeBannerPhoto.Command(ADMIN, UploadKey.of(key), PHOTO));
            replacePhoto.execute(new ReplaceHomeBannerPhoto.Command(ADMIN, UploadKey.of(key), PHOTO));
            publish.execute(new PublishHomeBanner.Command(ADMIN));
            publish.execute(new PublishHomeBanner.Command(ADMIN));
            hide.execute(new HideHomeBanner.Command(ADMIN));
            hide.execute(new HideHomeBanner.Command(ADMIN));

            ActivitySubject banner = ActivitySubject.of(SubjectType.HOME_BANNER);
            assertThat(activity.entries().subList(1, activity.entries().size())).containsExactly(
                entry(ActivityAction.HOME_BANNER_PHOTO_REPLACED, banner),
                entry(ActivityAction.HOME_BANNER_PUBLISHED, banner),
                entry(ActivityAction.HOME_BANNER_HIDDEN, banner));
        }
    }
}
