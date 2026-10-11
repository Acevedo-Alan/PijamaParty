package com.pijamaparty.store.catalog.adapter.in.event;

import com.pijamaparty.store.catalog.api.CategoryRenamed;
import com.pijamaparty.store.catalog.api.ProductChanged;
import com.pijamaparty.store.catalog.application.RefreshSearchVector;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-26: refreshes the search index when the searchable texts change. A plain (synchronous) listener, so it runs in
 * the publishing transaction and the index commits or rolls back with the change.
 */
@Component
class SearchIndexListener {

    private final RefreshSearchVector refreshSearchVector;

    SearchIndexListener(RefreshSearchVector refreshSearchVector) {
        this.refreshSearchVector = refreshSearchVector;
    }

    @EventListener
    void on(ProductChanged event) {
        refreshSearchVector.execute(new RefreshSearchVector.Command.OfProduct(ProductId.of(event.productId())));
    }

    @EventListener
    void on(CategoryRenamed event) {
        refreshSearchVector.execute(new RefreshSearchVector.Command.OfCategory(CategoryId.of(event.categoryId())));
    }
}
