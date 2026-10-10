package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HideProductService implements HideProduct {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final Clock clock;

    HideProductService(LoadProducts loadProducts, SaveProduct saveProduct, Clock clock) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.clock = clock;
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
        product.hide(clock.instant());
        if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
            throw new IllegalStateException("hiding touches no unique value or category");
        }
        return new Result.Hidden(ProductView.of(product));
    }
}
