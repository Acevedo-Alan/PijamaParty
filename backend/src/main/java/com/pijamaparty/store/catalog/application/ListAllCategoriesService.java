package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListAllCategoriesService implements ListAllCategories {

    private final LoadCategories categories;

    ListAllCategoriesService(LoadCategories categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Listed(categories.findAll().stream().map(CategoryView::of).toList());
    }
}
