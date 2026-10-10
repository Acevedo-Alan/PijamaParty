package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteCategoryService implements DeleteCategory {

    private final RemoveCategory removeCategory;

    DeleteCategoryService(RemoveCategory removeCategory) {
        this.removeCategory = removeCategory;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return switch (removeCategory.remove(command.categoryId())) {
            case REMOVED -> new Result.Deleted();
            case NOT_FOUND -> new Result.NotFound();
            case HAS_PRODUCTS -> new Result.HasProducts();
        };
    }
}
