package com.pijamaparty.store.catalog.application;

import java.util.List;

/**
 * Outbound read port of the sitemap (T-E6-23): the slugs of the listed products, listed as in the public grid
 * (UC-01): published, of an active category, with an active priced variant.
 */
public interface ListedProductSlugs {

    /** The slugs of the listed products, newest first, at most {@code limit}. */
    List<String> newestFirst(int limit);
}
