package com.pijamaparty.store.catalog.application;

import java.util.List;

/** T-E6-23 (RNF-31.1): the catalog's pages a search engine may index, for the sitemap. */
public interface ListSitemapPages {

    /** The format allows 50,000 URLs per file; the rest of the sitemap's pages fit in what is left. */
    int MAX_PRODUCTS = 49_000;

    Result execute();

    sealed interface Result {

        /** The active categories by position, and the listed products newest first. */
        record Listed(List<String> categorySlugs, List<String> productSlugs) implements Result {

            public Listed {
                categorySlugs = List.copyOf(categorySlugs);
                productSlugs = List.copyOf(productSlugs);
            }
        }
    }
}
