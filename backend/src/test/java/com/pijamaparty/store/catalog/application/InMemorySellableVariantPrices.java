package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Fake of {@link SellableVariantPrices} over the category and product fakes, with the domain's rules. */
public class InMemorySellableVariantPrices implements SellableVariantPrices {

    private final LoadCategories categories;
    private final InMemoryProductStore products;

    public InMemorySellableVariantPrices(LoadCategories categories, InMemoryProductStore products) {
        this.categories = categories;
        this.products = products;
    }

    @Override
    public Map<VariantId, Money> pricesOf(Collection<VariantId> variants) {
        Map<VariantId, Money> prices = new HashMap<>();
        for (Product product : products.all()) {
            boolean listed = product.published() && categories.findById(product.details().categoryId())
                .map(Category::active)
                .orElse(false);
            if (listed) {
                product.sellableVariants().stream()
                    .filter(variant -> variants.contains(variant.id()))
                    .forEach(variant -> prices.put(variant.id(), variant.price().orElseThrow()));
            }
        }
        return prices;
    }
}
