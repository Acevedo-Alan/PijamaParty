package com.pijamaparty.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * T-E6-01: the system clock moved forward by an offset the end-to-end tests control, so a 30-minute reservation
 * (UC-16) expires without waiting. Every copy made by {@link #withZone} shares the offset. Test sources only.
 */
final class AdjustableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    AdjustableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private AdjustableClock(Clock base, AtomicReference<Duration> offset) {
        this.base = Objects.requireNonNull(base, "base");
        this.offset = offset;
    }

    void advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("the clock only moves forward");
        }
        offset.accumulateAndGet(amount, Duration::plus);
    }

    void reset() {
        offset.set(Duration.ZERO);
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
