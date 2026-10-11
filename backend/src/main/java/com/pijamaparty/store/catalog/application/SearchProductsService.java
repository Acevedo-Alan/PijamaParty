package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.SearchText;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SearchProductsService implements SearchProducts {

    private final LoadCategories categories;
    private final ProductSearchQuery search;

    SearchProductsService(LoadCategories categories, ProductSearchQuery search) {
        this.categories = categories;
        this.search = search;
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
        // One more than a page tells whether another page follows.
        List<ProductListingItem> rows = search.search(text.get(), filter.get(), query.sort(),
            Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
