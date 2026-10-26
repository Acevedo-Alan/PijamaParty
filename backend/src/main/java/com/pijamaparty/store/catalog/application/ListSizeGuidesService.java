package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One guide read per active category: the shop has few categories, so no batch read is worth a new port. */
@Service
class ListSizeGuidesService implements ListSizeGuides {

    private final LoadCategories categories;
    private final LoadSizeGuides guides;

    ListSizeGuidesService(LoadCategories categories, LoadSizeGuides guides) {
        this.categories = categories;
        this.guides = guides;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute() {
        return new Result.Listed(categories.findAll().stream()
            .filter(Category::active)
            .flatMap(category -> guides.findByCategory(category.id())
                .map(guide -> new CategoryGuide(category.slug().value(), category.name().value(),
                    SizeGuideView.of(guide)))
                .stream())
            .toList());
    }
}
