package com.pijamaparty.store.backoffice.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-46: what the store has, read from its settings and its catalog at the moment of the read.
 *
 * @param categoriesWithoutSizeGuide active categories with a published product and no size guide
 * @param productsWithoutPhoto       published products without photos, newest first
 */
public record SetupFacts(boolean whatsApp, boolean email, boolean instagram, boolean homeBannerPublished,
                         Named categoriesWithoutSizeGuide, Named productsWithoutPhoto) {

    public SetupFacts {
        Objects.requireNonNull(categoriesWithoutSizeGuide, "categoriesWithoutSizeGuide");
        Objects.requireNonNull(productsWithoutPhoto, "productsWithoutPhoto");
    }

    /** A category or a product the guide names and links to. */
    public record Thing(UUID id, String name) {

        public Thing {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    /** The first things named and how many there are in all. */
    public record Named(List<Thing> first, long total) {

        public static final Named NONE = new Named(List.of(), 0);

        public Named {
            first = List.copyOf(first);
            if (total < first.size()) {
                throw new IllegalArgumentException("the total counts the things named");
            }
        }

        public boolean isEmpty() {
            return total == 0;
        }
    }
}
