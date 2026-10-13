package com.pijamaparty.store.payments.domain;

/**
 * UC-15: what the provider says of a payment, once translated by its adapter. {@code OTHER} covers what the store does
 * not act on, such as a refund or a chargeback (done from the provider's panel, out of scope in v1).
 */
public enum VerifiedStatus {
    APPROVED,
    REJECTED,
    PENDING,
    OTHER
}
