package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetSizeGuideService implements GetSizeGuide {

    private final LoadCategories categories;
    private final LoadSizeGuides guides;

    GetSizeGuideService(LoadCategories categories, LoadSizeGuides guides) {
        this.categories = categories;
        this.guides = guides;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return categories.findById(query.categoryId())
            .<Result>map(category -> new Result.Found(CategoryView.of(category),
                guides.findByCategory(category.id()).map(SizeGuideView::of)))
            .orElseGet(Result.NotFound::new);
    }
}
