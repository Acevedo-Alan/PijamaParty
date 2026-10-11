package com.pijamaparty.store.catalog.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * A product's name, category, material, description or variants changed (UC-06). Published in the transaction of the
 * change; listeners run in it (UC-26: the search index).
 */
public record ProductChanged(UUID productId) implements DomainEvent {

    public ProductChanged {
        Objects.requireNonNull(productId, "productId");
    }
}
