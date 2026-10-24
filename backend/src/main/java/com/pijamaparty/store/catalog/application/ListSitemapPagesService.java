package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListSitemapPagesService implements ListSitemapPages {

    private final LoadCategories categories;
    private final ListedProductSlugs products;

    ListSitemapPagesService(LoadCategories categories, ListedProductSlugs products) {
        this.categories = categories;
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute() {
        return new Result.Listed(
            categories.findAll().stream().filter(Category::active).map(category -> category.slug().value()).toList(),
            products.newestFirst(MAX_PRODUCTS));
    }
}
