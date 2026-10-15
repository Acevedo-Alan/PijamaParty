package com.pijamaparty.store.payments.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * UC-14 (T-E3b-02): when a MODO payment request expires. At most 10 minutes, and never after the order's reservation
 * (UC-16), except that MODO refuses less than 5 minutes: then 5. A payment approved after the reservation follows
 * UC-15 (the order stays expired and the refund is flagged).
 */
public final class PaymentRequestExpiry {

    public static final Duration MIN = Duration.ofMinutes(5);
    public static final Duration MAX = Duration.ofMinutes(10);

    private PaymentRequestExpiry() {
    }

    /**
     * The expiry of a payment request created at {@code now} for a reservation that ends at {@code reservationEnd}, in
     * whole seconds as MODO takes it: rounded down, except that the minimum is rounded up.
     */
    public static Instant of(Instant now, Instant reservationEnd) {
        Instant latest = now.plus(MAX);
        Instant earliest = ceilToSecond(now.plus(MIN));
        Instant expiry = (reservationEnd.isBefore(latest) ? reservationEnd : latest).truncatedTo(ChronoUnit.SECONDS);
        return expiry.isBefore(earliest) ? earliest : expiry;
    }

    private static Instant ceilToSecond(Instant instant) {
        Instant floor = instant.truncatedTo(ChronoUnit.SECONDS);
        return floor.equals(instant) ? floor : floor.plusSeconds(1);
    }
}
