package com.pijamaparty.store.payments.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * UC-14 (T-E3-32): a card charge whose answer never came blocks other cards of the order for this long; the provider
 * may or may not have charged (decided 2026-11-06).
 */
public final class CardChargeLock {

    public static final Duration TIMEOUT = Duration.ofMinutes(10);

    private CardChargeLock() {
    }

    /** Charges that began before this are no longer held. */
    public static Instant staleBefore(Instant now) {
        return now.minus(TIMEOUT);
    }
}
