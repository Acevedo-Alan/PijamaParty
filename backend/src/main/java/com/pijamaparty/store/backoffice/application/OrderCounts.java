package com.pijamaparty.store.backoffice.application;

/** Outbound read port (T-E8-05, UC-33): how much order work waits, as counts only. */
public interface OrderCounts {

    /** Orders {@code PAID}, waiting to be prepared. */
    long paidWaiting();
}
