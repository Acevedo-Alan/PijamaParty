package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database steps of uploading and deleting a photo (UC-06), each its own transaction. They are a separate bean so
 * that the use cases convert, write and remove files outside any transaction, never holding a pooled connection. The
 * steps that change the product record it in the activity log (UC-37).
 */
@Service
class ProductImageTransactions {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final CatalogActivity activity;
    private final Clock clock;

    ProductImageTransactions(LoadProducts loadProducts, SaveProduct saveProduct, CatalogActivity activity,
                             Clock clock) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.activity = activity;
        this.clock = clock;
    }

    /** Whether the photo can be added, without locking; empty when there is no such product. */
    @Transactional(readOnly = true)
    Optional<Product.ImageUpload> check(ProductId productId, UploadKey uploadKey, ContentHash contentHash,
                                        Optional<Color> color) {
        return loadProducts.findById(productId)
            .map(product -> product.checkUpload(uploadKey, contentHash, color));
    }

    /** Adds the photo under the product's lock, checking again; empty when there is no such product. */
    @Transactional
    Optional<Product.ImageUpload> add(Actor administrator, ProductId productId, Product.NewImage image) {
        return loadProducts.lockById(productId).map(product -> {
            Product.ImageUpload outcome = product.addImage(image, clock.instant());
            if (outcome instanceof Product.ImageUpload.Added) {
                save(product);
                activity.record(CatalogEntries.product(administrator, ActivityAction.PRODUCT_IMAGE_UPLOADED,
                    product));
            }
            return outcome;
        });
    }

    /** Removes the photo; empty when there is no such product or photo. */
    @Transactional
    Optional<ProductImage> remove(Actor administrator, ProductId productId, ImageId imageId) {
        return loadProducts.lockById(productId).flatMap(product -> {
            Optional<ProductImage> removed = product.removeImage(imageId);
            removed.ifPresent(image -> {
                save(product);
                activity.record(CatalogEntries.product(administrator, ActivityAction.PRODUCT_IMAGE_DELETED,
                    product));
            });
            return removed;
        });
    }

    private void save(Product product) {
        if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
            throw new IllegalStateException("a photo touches no slug, SKU or category");
        }
    }
}
