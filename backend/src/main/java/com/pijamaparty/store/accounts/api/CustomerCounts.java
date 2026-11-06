package com.pijamaparty.store.accounts.api;

import java.time.Instant;

/**
 * UC-33 (T-E8-14): how many customers registered, for the back office's summary. Joins the caller's transaction, so
 * its security context decides which users are visible (RNF-27): the administrator sees the customers (V44), never
 * itself.
 */
public interface CustomerCounts {

    /** Every registered customer, and those registered at {@code since} or later. */
    Counts count(Instant since);

    record Counts(long registered, long registeredSince) {}
}
