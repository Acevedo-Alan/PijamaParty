package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateVariantService implements UpdateVariant {

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final CatalogEvents events;
    private final Clock clock;

    UpdateVariantService(LoadProducts loadProducts, SaveProduct saveProduct, CatalogEvents events, Clock clock) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Money> price = command.priceCents().flatMap(Variant::priceOf);
        if (command.priceCents().isPresent() && price.isEmpty()) {
            return new Result.InvalidPrice();
        }
        Optional<Product> found = loadProducts.lockByVariantId(command.variantId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        Product product = found.get();
        return switch (product.changeVariant(command.variantId(), price, command.active(), clock.instant())) {
            case Product.VariantOutcome.UnknownVariant unknown -> new Result.NotFound();
            case Product.VariantOutcome.InvalidPrice invalid -> new Result.InvalidPrice();
            case Product.VariantOutcome.LastActivePricedVariant last -> new Result.LastActivePricedVariant();
            case Product.VariantOutcome.Changed changed -> {
                if (saveProduct.update(product) != SaveProduct.Outcome.SAVED) {
                    throw new IllegalStateException("changing a variant touches no unique value or category");
                }
                events.productChanged(product.id());
                yield new Result.Updated(ProductView.of(product));
            }
        };
    }
}
