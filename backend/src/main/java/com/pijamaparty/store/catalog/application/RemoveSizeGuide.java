package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;

/** Outbound port: deletes the size guide of a category within the caller's transaction; nothing without one. */
public interface RemoveSizeGuide {

    void remove(CategoryId categoryId);
}
