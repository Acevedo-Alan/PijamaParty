package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.List;
import java.util.Optional;

/** Outbound port: reads categories within the caller's transaction. */
public interface LoadCategories {

    Optional<Category> findById(CategoryId id);

    /** Every category, active or not, by position. */
    List<Category> findAll();
}
