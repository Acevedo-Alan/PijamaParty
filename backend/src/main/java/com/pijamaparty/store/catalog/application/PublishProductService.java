package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublishProductService implements PublishProduct {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final CatalogActivity activity;
    private final Clock clock;

    PublishProductService(LoadProducts loadProducts, SaveProduct saveProduct, CatalogActivity activity, Clock clock) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.activity = activity;
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
        boolean wasPublished = product.published();
        return switch (product.publish(clock.instant())) {
            case Product.PublishOutcome.NoActivePricedVariant none -> new Result.NoActivePricedVariant();
            case Product.PublishOutcome.Published published -> {
                if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
                    throw new IllegalStateException("publishing touches no unique value or category");
                }
                if (!wasPublished) {
                    activity.record(CatalogEntries.product(command.actor(), ActivityAction.PRODUCT_PUBLISHED,
                        product));
                }
                yield new Result.Published(ProductView.of(product));
            }
        };
    }
}
