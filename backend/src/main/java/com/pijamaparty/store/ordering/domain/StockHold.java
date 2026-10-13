package com.pijamaparty.store.ordering.domain;

import java.time.Duration;
import java.time.Instant;

/** UC-16: an order holds its stock for 30 minutes without an approved payment (decided in T-E3-01). */
public final class StockHold {

    public static final Duration LENGTH = Duration.ofMinutes(30);

    private StockHold() {
    }

    /** When the reservation of an order placed at {@code placedAt} expires. */
    public static Instant expiryOf(Instant placedAt) {
        return placedAt.plus(LENGTH);
    }
}
