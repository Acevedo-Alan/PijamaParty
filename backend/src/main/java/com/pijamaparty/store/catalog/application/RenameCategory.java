package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-05: the administrator renames a category; the slug is regenerated from the new name. */
public interface RenameCategory {

    Result execute(Command command);

    /** The name as typed: the use case trims and validates it. */
    record Command(Actor actor, CategoryId categoryId, String name) {}

    sealed interface Result {

        record Renamed(CategoryView category) implements Result {}

        record NotFound() implements Result {}

        /** Another category already has the slug the new name produces. */
        record SlugAlreadyUsed(String slug) implements Result {}

        /** Empty, longer than 80 characters, or without letters or digits to build a slug from. */
        record InvalidName() implements Result {}

        record Forbidden() implements Result {}
    }
}
