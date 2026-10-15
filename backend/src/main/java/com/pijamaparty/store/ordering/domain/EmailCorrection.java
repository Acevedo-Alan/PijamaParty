package com.pijamaparty.store.ordering.domain;

/** UC-17 (T-E3-23, decided): what correcting the buyer's email does, by the order's status. */
public enum EmailCorrection {

    /** Awaiting payment: only the email changes; the confirmation goes there once the order is paid. */
    BEFORE_CONFIRMATION,

    /** Paid, from {@code PAID} to {@code DELIVERED}: the email changes and the confirmation is sent again. */
    RESEND_CONFIRMATION,

    /** Cancelled or expired: the order is closed, and its email is not corrected. */
    CLOSED;

    public static EmailCorrection of(OrderStatus status) {
        return switch (status) {
            case AWAITING_PAYMENT -> BEFORE_CONFIRMATION;
            case PAID, PREPARING, SHIPPED, DELIVERED -> RESEND_CONFIRMATION;
            case CANCELLED, EXPIRED -> CLOSED;
        };
    }
}
