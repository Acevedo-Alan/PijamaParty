package com.pijamaparty.store.ordering.api;

/**
 * T-E8-05 and UC-33: how much order work waits, for the back-office menu and summary. Read in its own transaction, or
 * the caller's, as the caller: the administrator, whom Row Level Security lets read every order. Counts only, no
 * personal data.
 */
public interface OrderWorkload {

    /** Orders {@code PAID}, waiting to be prepared. */
    long paidWaiting();
}
