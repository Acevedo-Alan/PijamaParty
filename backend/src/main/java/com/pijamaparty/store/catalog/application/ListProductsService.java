package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListProductsService implements ListProducts {

    private final LoadCategories categories;
    private final ProductListingQuery listing;

    ListProductsService(LoadCategories categories, ProductListingQuery listing) {
        this.categories = categories;
        this.listing = listing;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
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
        List<ProductListingItem> rows = listing.list(filter.get(), query.sort(),
            Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
