package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import java.time.Instant;

/**
 * Outbound read port of the summary's customer totals (UC-33): the users with role {@code CUSTOMER}, within the
 * caller's transaction, whose security context decides which users are visible.
 */
public interface CountCustomers {

    /** All of them, and those created at {@code since} or later. */
    Counts count(Instant since);
}
