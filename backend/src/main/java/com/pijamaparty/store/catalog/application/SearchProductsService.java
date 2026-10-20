package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.SearchText;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SearchProductsService implements SearchProducts {

    private final LoadCategories categories;
    private final ProductSearchQuery search;
    private final Clock clock;

    SearchProductsService(LoadCategories categories, ProductSearchQuery search, Clock clock) {
        this.categories = categories;
        this.search = search;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<SearchText> text = SearchText.parse(query.text());
        if (text.isEmpty()) {
            return new Result.TextTooShort();
        }
        Optional<CategoryId> category = query.category().flatMap(slug -> ShopCategories.activeBySlug(categories, slug));
        if (query.category().isPresent() && category.isEmpty()) {
            return new Result.UnknownCategory();
        }
        Optional<ProductFilter> filter = ProductFilter.parse(category, query.sizes(), query.colors(),
            query.materials(), query.seasons(), query.audiences(), query.minPriceCents(), query.maxPriceCents());
        if (filter.isEmpty()) {
            return new Result.InvalidFilter();
        }
        int offset = Math.multiplyExact(query.page(), PAGE_SIZE);
        // One more than a page tells whether another page follows.
        List<ListedProduct> rows = search.search(text.get(), filter.get(), query.sort(), offset, PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<ListedProduct> shown = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        // The last page tells the total without counting, unless it is past the end.
        long total = !hasMore && (offset == 0 || !shown.isEmpty())
            ? offset + shown.size()
            : search.count(text.get(), filter.get());
        Instant now = clock.instant();
        return new Result.Listed(shown.stream().map(row -> row.toItem(now)).toList(), hasMore, total);
    }
}
