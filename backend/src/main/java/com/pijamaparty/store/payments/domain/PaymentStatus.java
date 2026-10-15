package com.pijamaparty.store.payments.domain;

/** The status of a payment as last verified with its provider (R-06), the {@code payment.status} column (V15). */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED;

    /** RNF-20.5: an approved payment is final; a pending or rejected one may still be paid (UC-15). */
    public boolean isFinal() {
        return this == APPROVED;
    }
}
