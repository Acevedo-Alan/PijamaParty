package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-29 (T-E3-25): a withdrawal was requested for an order. Published in the request's transaction, as the system;
 * listeners run in it, so the emails they enqueue commit or roll back with the request. The code and the email are
 * not in the event: they are read through {@link WithdrawalNotices} when the emails are sent.
 */
public record WithdrawalRequested(UUID requestId, UUID orderId, long number) implements DomainEvent {

    public WithdrawalRequested {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
    }
}
