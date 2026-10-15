package com.pijamaparty.store.ordering.domain;

/** UC-29 (T-E3-25, decided): which orders a withdrawal can be requested for, by their status. */
public final class WithdrawalEligibility {

    private WithdrawalEligibility() {
    }

    /**
     * Paid, from {@code PAID} to {@code DELIVERED}. Awaiting payment, expired or cancelled, there is no payment to
     * return.
     */
    public static boolean allows(OrderStatus status) {
        return switch (status) {
            case PAID, PREPARING, SHIPPED, DELIVERED -> true;
            case AWAITING_PAYMENT, EXPIRED, CANCELLED -> false;
        };
    }
}
