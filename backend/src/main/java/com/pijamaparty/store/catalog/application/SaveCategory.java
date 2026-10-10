package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;

/**
 * Outbound port: writes categories within the caller's transaction. The slug is unique: a category whose slug another
 * one already uses is not saved, and the caller's transaction stays usable.
 */
public interface SaveCategory {

    /** Inserts a new category. Never answers {@link Outcome#NOT_FOUND}. */
    Outcome add(Category category);

    /** Updates a stored category; one deleted in the meantime is not brought back ({@link Outcome#NOT_FOUND}). */
    Outcome update(Category category);

    enum Outcome {
        SAVED,
        SLUG_ALREADY_USED,
        NOT_FOUND
    }
}
