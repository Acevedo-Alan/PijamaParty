package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReorderProductImagesService implements ReorderProductImages {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;

    ReorderProductImagesService(LoadProducts loadProducts, SaveProduct saveProduct) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
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
        if (!product.reorderImages(command.order())) {
            return new Result.InvalidOrder();
        }
        if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
            throw new IllegalStateException("reordering photos touches no slug, SKU or category");
        }
        return new Result.Reordered(ProductView.of(product));
    }
}
