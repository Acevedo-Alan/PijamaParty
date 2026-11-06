package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.PublishedProducts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-33: the published products for the totals of the back-office summary. */
@Service
class PublishedProductsService implements PublishedProducts {

    private final CountPublishedProducts products;

    PublishedProductsService(CountPublishedProducts products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return products.publishedProducts();
    }
}
