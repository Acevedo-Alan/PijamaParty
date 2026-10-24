package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreateProductService implements CreateProduct {

    private final LoadCategories loadCategories;
    private final SaveProduct saveProduct;
    private final CatalogEvents events;
    private final CatalogActivity activity;
    private final IdGenerator ids;
    private final Clock clock;

    CreateProductService(LoadCategories loadCategories, SaveProduct saveProduct, CatalogEvents events,
                         CatalogActivity activity, IdGenerator ids, Clock clock) {
        this.loadCategories = loadCategories;
        this.saveProduct = saveProduct;
        this.events = events;
        this.activity = activity;
        this.ids = ids;
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
        if (loadCategories.findById(details.categoryId()).isEmpty()) {
            return new Result.UnknownCategory();
        }
        Product product = Product.create(ProductId.of(ids.newId()), details, clock.instant());
        return switch (saveProduct.add(product)) {
            case SAVED -> {
                events.productChanged(product.id());
                activity.record(CatalogEntries.product(command.actor(), ActivityAction.PRODUCT_CREATED, product));
                yield new Result.Created(ProductView.of(product));
            }
            case SLUG_ALREADY_USED -> new Result.SlugAlreadyUsed(product.slug().value());
            // The category was deleted after it was read.
            case UNKNOWN_CATEGORY -> new Result.UnknownCategory();
            case SKU_ALREADY_USED -> throw new IllegalStateException("a new product has no variants, so no SKUs");
        };
    }
}
