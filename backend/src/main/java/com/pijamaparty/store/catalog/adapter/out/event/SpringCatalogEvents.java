package com.pijamaparty.store.catalog.adapter.out.event;

import com.pijamaparty.store.catalog.api.CategoryRenamed;
import com.pijamaparty.store.catalog.api.ProductChanged;
import com.pijamaparty.store.catalog.application.CatalogEvents;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the events of {@code catalog.api} through Spring; plain listeners run at once, in the caller's thread. */
@Component
class SpringCatalogEvents implements CatalogEvents {

    private final ApplicationEventPublisher publisher;

    SpringCatalogEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void productChanged(ProductId product) {
        publisher.publishEvent(new ProductChanged(product.value()));
    }

    @Override
    public void categoryRenamed(CategoryId category) {
        publisher.publishEvent(new CategoryRenamed(category.value()));
    }
}
