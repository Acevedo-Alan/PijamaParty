package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import java.util.Optional;

/** Outbound port: reads size guides within the caller's transaction. */
public interface LoadSizeGuides {

    /** The guide of the category; empty without one or for an unknown category. */
    Optional<SizeGuide> findByCategory(CategoryId categoryId);
}
