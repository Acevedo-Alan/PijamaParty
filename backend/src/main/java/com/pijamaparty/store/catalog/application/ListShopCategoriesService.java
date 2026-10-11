package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListShopCategoriesService implements ListShopCategories {

    private final LoadCategories categories;

    ListShopCategoriesService(LoadCategories categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute() {
        return new Result.Listed(categories.findAll().stream()
            .filter(Category::active)
            .map(category -> new ShopCategory(category.slug().value(), category.name().value()))
            .toList());
    }
}
