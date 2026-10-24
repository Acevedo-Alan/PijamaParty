package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.FieldChanges;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
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
    private final CatalogEvents events;
    private final CatalogActivity activity;
    private final Clock clock;

    UpdateProductService(LoadCategories loadCategories, LoadProducts loadProducts, SaveProduct saveProduct,
                         CatalogEvents events, CatalogActivity activity, Clock clock) {
        this.loadCategories = loadCategories;
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.events = events;
        this.activity = activity;
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
        Optional<Category> category = loadCategories.findById(details.categoryId());
        if (category.isEmpty()) {
            return new Result.UnknownCategory();
        }
        Product product = found.get();
        ProductDetails previous = product.details();
        product.update(details, clock.instant());
        return switch (saveProduct.update(product)) {
            case SAVED -> {
                events.productChanged(product.id());
                FieldChanges changes = changes(previous, details, category.get());
                if (!changes.isEmpty()) {
                    activity.record(CatalogEntries.product(command.actor(), ActivityAction.PRODUCT_UPDATED, product,
                        changes.list()));
                }
                yield new Result.Updated(ProductView.of(product));
            }
            case SLUG_ALREADY_USED -> new Result.SlugAlreadyUsed(product.slug().value());
            // The category was deleted after it was read.
            case UNKNOWN_CATEGORY -> new Result.UnknownCategory();
            case SKU_ALREADY_USED -> throw new IllegalStateException("editing details creates no variants");
        };
    }

    /** UC-37: the details that changed; categories by name. */
    private FieldChanges changes(ProductDetails previous, ProductDetails details, Category category) {
        return new FieldChanges()
            .add("name", previous.name().value(), details.name().value())
            .add("description", previous.description().value(), details.description().value())
            .add("category", previous.categoryId().equals(details.categoryId())
                ? category.name().value() : categoryName(previous.categoryId()), category.name().value())
            .add("material", previous.material().value(), details.material().value())
            .add("season", previous.season().name(), details.season().name())
            .add("audience", previous.audience().name(), details.audience().name());
    }

    /** The name of the product's previous category; a category with products is never deleted (UC-05). */
    private String categoryName(CategoryId id) {
        return loadCategories.findById(id).map(category -> category.name().value()).orElse(null);
    }
}
