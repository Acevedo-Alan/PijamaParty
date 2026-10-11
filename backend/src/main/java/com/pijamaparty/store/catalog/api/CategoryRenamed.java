package com.pijamaparty.store.catalog.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/** A category got a new name (UC-05). Published in the transaction of the change; listeners run in it. */
public record CategoryRenamed(UUID categoryId) implements DomainEvent {

    public CategoryRenamed {
        Objects.requireNonNull(categoryId, "categoryId");
    }
}
