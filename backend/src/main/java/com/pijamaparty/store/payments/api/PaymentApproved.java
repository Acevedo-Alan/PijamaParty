package com.pijamaparty.store.payments.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-15: the provider approved the payment of an order for its exact amount, verified by asking the provider (R-06).
 * Published once per payment, in the transaction that records the approval; listeners run in it, so what they change
 * commits or rolls back with the payment.
 */
public record PaymentApproved(UUID orderId, PaymentProvider provider) implements DomainEvent {

    public PaymentApproved {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(provider, "provider");
    }
}
