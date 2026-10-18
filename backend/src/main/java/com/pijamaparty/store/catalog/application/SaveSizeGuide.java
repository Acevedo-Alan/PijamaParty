package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.SizeGuide;

/**
 * Outbound port: stores a size guide within the caller's transaction, replacing the category's previous one. A guide
 * of a category that is gone is not saved, and the caller's transaction stays usable.
 */
public interface SaveSizeGuide {

    Outcome save(SizeGuide guide);

    enum Outcome {
        SAVED,
        CATEGORY_NOT_FOUND
    }
}
