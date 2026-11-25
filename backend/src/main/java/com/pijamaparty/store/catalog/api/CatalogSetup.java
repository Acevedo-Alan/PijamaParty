package com.pijamaparty.store.catalog.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-46: what the store setup guide reads of the catalog, to say what is missing. Read only; joins the caller's
 * transaction.
 */
public interface CatalogSetup {

    /** Active categories with at least one published product and no size guide, in the categories' order. */
    Named categoriesWithoutSizeGuide(int limit);

    /** Published products without any photo, newest first. */
    Named publishedProductsWithoutPhoto(int limit);

    /** Whether the home page's banner (UC-32) is published. */
    boolean homeBannerPublished();

    /** One category or product, as the guide names it and links to it. */
    record Thing(UUID id, String name) {

        public Thing {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * The first things that match, at most the limit asked, and how many match in all.
     *
     * @param total all of them, never fewer than {@code first}
     */
    record Named(List<Thing> first, long total) {

        public Named {
            first = List.copyOf(first);
            if (total < first.size()) {
                throw new IllegalArgumentException("the total counts the things named");
            }
        }
    }
}
