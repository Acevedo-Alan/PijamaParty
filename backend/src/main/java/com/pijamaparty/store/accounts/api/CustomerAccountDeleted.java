package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-28 (T-E4-16): a customer deleted its account. Published in the deletion's transaction, as that customer, once its
 * user row is gone; listeners run in it (ordering unlinks the orders, cart deletes the cart), so all of it commits or
 * none does. Each listener is idempotent: a second run finds nothing to change.
 */
public record CustomerAccountDeleted(UUID userId) implements DomainEvent {

    public CustomerAccountDeleted {
        Objects.requireNonNull(userId, "userId");
    }
}
