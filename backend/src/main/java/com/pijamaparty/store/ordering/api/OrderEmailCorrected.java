package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-17 (T-E3-23): the buyer corrected the email of a paid order, so its confirmation is sent again. Published in the
 * correction's transaction, as the buyer; listeners run in it, so what they enqueue commits or rolls back with the
 * correction. The new email is not in the event: it is read from the order when the confirmation is sent.
 */
public record OrderEmailCorrected(UUID orderId, long number) implements DomainEvent {

    public OrderEmailCorrected {
        Objects.requireNonNull(orderId, "orderId");
    }
}
