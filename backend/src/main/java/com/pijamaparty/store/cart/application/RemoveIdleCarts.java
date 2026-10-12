package com.pijamaparty.store.cart.application;

import java.time.Duration;

/**
 * T-E2-04: the daily cleanup deletes the carts, guests' and customers' alike, without a change for more than
 * {@link #IDLE_PERIOD}. Runs as {@code SYSTEM}, the only role Row Level Security lets reach every cart (RNF-27).
 */
public interface RemoveIdleCarts {

    /** Also how long a guest's {@code cart_token} cookie lives after its last cart change. */
    Duration IDLE_PERIOD = Duration.ofDays(30);

    Result execute();

    record Result(int removed) {}
}
