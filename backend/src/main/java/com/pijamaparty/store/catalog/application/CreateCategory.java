package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-05: the administrator creates a category; its slug comes from the name and it goes last. */
public interface CreateCategory {

    Result execute(Command command);

    /** The name as typed: the use case trims and validates it. */
    record Command(Actor actor, String name) {}

    sealed interface Result {

        record Created(CategoryView category) implements Result {}

        /** Another category already has the slug this name produces. */
        record SlugAlreadyUsed(String slug) implements Result {}

        /** Empty, longer than 80 characters, or without letters or digits to build a slug from. */
        record InvalidName() implements Result {}

        record Forbidden() implements Result {}
    }
}
