package com.pijamaparty.store.cart.application;

import java.time.Instant;

/** Outbound port: deletes idle carts with their items, within the caller's transaction. */
public interface DeleteIdleCarts {

    /** Deletes every cart whose last change is before {@code cutoff}, and answers how many. */
    int deleteUpdatedBefore(Instant cutoff);
}
