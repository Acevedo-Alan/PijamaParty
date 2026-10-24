package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReorderProductImagesService implements ReorderProductImages {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final CatalogActivity activity;

    ReorderProductImagesService(LoadProducts loadProducts, SaveProduct saveProduct, CatalogActivity activity) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.activity = activity;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Product> found = loadProducts.lockById(command.productId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        Product product = found.get();
        List<ImageId> before = order(product);
        if (!product.reorderImages(command.order())) {
            return new Result.InvalidOrder();
        }
        if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
            throw new IllegalStateException("reordering photos touches no slug, SKU or category");
        }
        if (!before.equals(order(product))) {
            activity.record(CatalogEntries.product(command.actor(), ActivityAction.PRODUCT_IMAGES_REORDERED,
                product));
        }
        return new Result.Reordered(ProductView.of(product));
    }

    private static List<ImageId> order(Product product) {
        return product.images().stream().map(ProductImage::id).toList();
    }
}
