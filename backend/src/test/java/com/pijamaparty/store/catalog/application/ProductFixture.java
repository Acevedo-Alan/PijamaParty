package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The fakes and services of the UC-06 use-case tests, wired as Spring would, around one category. */
final class ProductFixture {

    static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    static final Actor CUSTOMER = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());
    static final CategoryId CATEGORY = CategoryId.of(UUID.fromString("00000000-0000-0000-0000-0000000000ca"));

    final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    final InMemoryProductStore products = new InMemoryProductStore(categories);
    final InMemoryIdGenerator ids = new InMemoryIdGenerator();
    final InMemorySkuGenerator skus = new InMemorySkuGenerator();
    final InMemoryOpenStockLevels stockLevels = new InMemoryOpenStockLevels();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    final CreateProduct create = new CreateProductService(categories, products, ids, clock);
    final UpdateProduct update = new UpdateProductService(categories, products, products, clock);
    final GenerateVariantMatrix generate =
        new GenerateVariantMatrixService(products, products, stockLevels, ids, skus, clock);
    final UpdateVariant updateVariant = new UpdateVariantService(products, products, clock);
    final PublishProduct publish = new PublishProductService(products, products, clock);
    final HideProduct hide = new HideProductService(products, products, clock);
    final ListAllProducts listAll = new ListAllProductsService(products);
    final GetProductForAdmin get = new GetProductForAdminService(products);
    final InMemoryImageProcessor imageProcessor = new InMemoryImageProcessor();
    final InMemoryImageStore imageStore = new InMemoryImageStore();
    final ProductImageTransactions imageTransactions = new ProductImageTransactions(products, products, clock);
    final UploadProductImage uploadImage =
        new UploadProductImageService(imageTransactions, imageProcessor, imageStore, ids);
    final ReorderProductImages reorderImages = new ReorderProductImagesService(products, products);
    final DeleteProductImage deleteImage = new DeleteProductImageService(imageTransactions, imageStore);

    ProductFixture() {
        categories.save(Category.create(CATEGORY, new CategoryName("Verano"), List.of(), NOW));
    }

    static ProductDetailsInput details(String name) {
        return new ProductDetailsInput(CATEGORY, name, "Suave y fresco.", "Algodón", Season.SUMMER, Audience.WOMEN);
    }

    /** Creates a product as the administrator and returns it. */
    ProductView created(String name) {
        return ((CreateProduct.Result.Created) create.execute(new CreateProduct.Command(ADMIN, details(name))))
            .product();
    }

    /** Creates a product with the given matrix and returns it. */
    ProductView withMatrix(String name, List<String> sizes, List<String> colors) {
        ProductView product = created(name);
        return ((GenerateVariantMatrix.Result.Generated) generate.execute(
            new GenerateVariantMatrix.Command(ADMIN, product.id(), sizes, colors))).product();
    }

    /** Sets a price as the administrator. */
    ProductView priced(ProductView.VariantView variant, long cents) {
        return ((UpdateVariant.Result.Updated) updateVariant.execute(new UpdateVariant.Command(ADMIN, variant.id(),
            Optional.of(cents), Optional.empty()))).product();
    }
}
