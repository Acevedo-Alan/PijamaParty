package com.pijamaparty.store.catalog.application;

import java.util.List;

/** Outbound read port of the back-office product list (UC-06). */
public interface ProductSummaries {

    /** Every product, published or not, newest first, skipping {@code offset} and returning at most {@code limit}. */
    List<ProductSummaryView> list(int offset, int limit);
}
