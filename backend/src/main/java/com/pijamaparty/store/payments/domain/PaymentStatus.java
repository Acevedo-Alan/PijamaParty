package com.pijamaparty.store.payments.domain;

/** The status of a payment as last verified with its provider (R-06), the {@code payment.status} column (V15). */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED
}
