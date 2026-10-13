package com.pijamaparty.store.notifications.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * RNF-23: an email is tried up to {@value #MAX_ATTEMPTS} times, the 2nd to 5th at 1, 5, 15 and 60 minutes after the
 * one before.
 */
public final class RetrySchedule {

    public static final int MAX_ATTEMPTS = 5;

    private static final List<Duration> DELAYS =
        List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60));

    private RetrySchedule() {
    }

    /**
     * When to try again after the given attempt began at {@code now}. After the last one there is no retry: that is
     * how long it may take before it is given up as failed (it only matters when the process stopped while sending).
     *
     * @param attempt from 1 to {@value #MAX_ATTEMPTS}
     */
    public static Instant nextAttemptAt(int attempt, Instant now) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempt out of range: " + attempt);
        }
        Objects.requireNonNull(now, "now");
        return now.plus(DELAYS.get(Math.min(attempt, DELAYS.size()) - 1));
    }
}
