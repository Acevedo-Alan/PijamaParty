package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import java.time.Instant;

/**
 * Outbound read port (UC-33): the summary's customer totals, as {@code accounts.api.CustomerCounts} counts them, in
 * the caller's transaction.
 */
public interface CustomerFigures {

    /** Every registered customer, and those registered at {@code since} or later. */
    Counts count(Instant since);
}
