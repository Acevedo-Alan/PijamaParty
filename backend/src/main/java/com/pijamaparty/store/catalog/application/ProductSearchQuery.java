package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.SearchText;
import java.util.List;
import java.util.Optional;

/**
 * Outbound read port of the text search (UC-26) over the listed products of {@link ProductListingQuery}. It ignores
 * accents and case, understands Spanish singular and plural, and weighs the name most. A product matches through
 * its search vector as {@link SearchVectors} last refreshed it.
 */
public interface ProductSearchQuery {

    /**
     * The listed products that match {@code text} (web search syntax) and {@code filter}, skipping {@code offset}:
     * most relevant first when {@code sort} is empty, ties newest first.
     */
    List<ProductListingItem> search(SearchText text, ProductFilter filter, Optional<ProductSort> sort, int offset,
                                    int limit);

    /** Listed products whose texts hold every word of {@code text}, the last one as a prefix, most relevant first. */
    List<ProductSuggestion> suggest(SearchText text, int limit);
}
