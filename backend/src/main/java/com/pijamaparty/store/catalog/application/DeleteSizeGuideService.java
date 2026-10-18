package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteSizeGuideService implements DeleteSizeGuide {

    private final LoadCategories categories;
    private final RemoveSizeGuide removeSizeGuide;

    DeleteSizeGuideService(LoadCategories categories, RemoveSizeGuide removeSizeGuide) {
        this.categories = categories;
        this.removeSizeGuide = removeSizeGuide;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        if (categories.findById(command.categoryId()).isEmpty()) {
            return new Result.NotFound();
        }
        removeSizeGuide.remove(command.categoryId());
        return new Result.Deleted();
    }
}
