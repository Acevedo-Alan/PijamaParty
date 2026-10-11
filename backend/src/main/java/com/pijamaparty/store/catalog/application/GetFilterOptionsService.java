package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetFilterOptionsService implements GetFilterOptions {

    private final LoadCategories categories;
    private final FilterOptionsQuery options;

    GetFilterOptionsService(LoadCategories categories, FilterOptionsQuery options) {
        this.categories = categories;
        this.options = options;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<CategoryId> category = query.category().flatMap(slug -> ShopCategories.activeBySlug(categories, slug));
        if (query.category().isPresent() && category.isEmpty()) {
            return new Result.UnknownCategory();
        }
        return new Result.Found(options.options(category));
    }
}
