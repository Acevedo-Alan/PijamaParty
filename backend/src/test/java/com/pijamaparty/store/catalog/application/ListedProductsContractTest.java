package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ContractTest;

/**
 * Arrangement shared by the contracts of the public catalog's read ports ({@link ProductListingQuery},
 * {@link FilterOptionsQuery}): they read categories, products and stock that other ports write, so each
 * implementation says how to store them.
 */
public abstract class ListedProductsContractTest<P> extends ContractTest<P> {

    protected abstract void givenCategory(Category category);

    /** Stores the product as it is; its category was given first. */
    protected abstract void givenProduct(Product product);

    /** The inventory's available stock of a variant; a variant never given has none. */
    protected abstract void givenAvailable(VariantId variant, int available);

    protected Category category(String name, boolean active) {
        Category category = ListingFixture.category(name, active);
        givenCategory(category);
        return category;
    }

    protected Product stored(Product product) {
        givenProduct(product);
        return product;
    }

    /** Stores the product and gives every variant of it {@code available} units. */
    protected Product stocked(Product product, int available) {
        givenProduct(product);
        product.variants().forEach(variant -> givenAvailable(variant.id(), available));
        return product;
    }
}
