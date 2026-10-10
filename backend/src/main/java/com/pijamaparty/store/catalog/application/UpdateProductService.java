package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateProductService implements UpdateProduct {

    private final LoadCategories loadCategories;
    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final Clock clock;

    UpdateProductService(LoadCategories loadCategories, LoadProducts loadProducts, SaveProduct saveProduct,
                         Clock clock) {
        this.loadCategories = loadCategories;
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
        ProductDetails details;
        switch (command.details().parse()) {
            case ProductDetailsInput.Parsed.Invalid invalid -> {
                return new Result.InvalidDetails(invalid.fields());
            }
            case ProductDetailsInput.Parsed.Valid valid -> details = valid.details();
        }
        Optional<Product> found = loadProducts.lockById(command.productId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        if (loadCategories.findById(details.categoryId()).isEmpty()) {
            return new Result.UnknownCategory();
        }
        Product product = found.get();
        product.update(details, clock.instant());
        return switch (saveProduct.update(product)) {
            case SAVED -> new Result.Updated(ProductView.of(product));
            case SLUG_ALREADY_USED -> new Result.SlugAlreadyUsed(product.slug().value());
            // The category was deleted after it was read.
            case UNKNOWN_CATEGORY -> new Result.UnknownCategory();
            case SKU_ALREADY_USED -> throw new IllegalStateException("editing details creates no variants");
        };
    }
}
