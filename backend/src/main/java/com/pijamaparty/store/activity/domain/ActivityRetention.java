package com.pijamaparty.store.activity.domain;

import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;

/** D-E8-11: entries are kept for 2 years, then the daily job deletes them. */
public final class ActivityRetention {

    public static final Period KEPT_FOR = Period.ofYears(2);

    private ActivityRetention() {
    }

    /** Entries that occurred before the returned instant are past their 2 years at {@code now}. */
    public static Instant cutoff(ZonedDateTime now) {
        return now.minus(KEPT_FOR).toInstant();
    }
}
