package com.pijamaparty.store.ordering.domain;

/** UC-29: where a withdrawal request stands. Only a {@link #RECEIVED} one is active, and only it may be resolved. */
public enum WithdrawalStatus {

    /** Active: the administrator still has to arrange the return and the refund. */
    RECEIVED,

    /** The administrator closed it (T-E3-26); the order may get another request. */
    RESOLVED;

    /** T-E3-26: any received request may be resolved, whatever its order's status; the administrator decides. */
    public boolean isResolvable() {
        return this == RECEIVED;
    }
}
