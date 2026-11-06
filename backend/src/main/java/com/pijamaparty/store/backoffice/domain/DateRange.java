package com.pijamaparty.store.backoffice.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Store dates from {@code from} to {@code to}, both included (R-07); their instants depend on the store's zone. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("a range ends on or after its start");
        }
    }

    /** The number of days, both ends included. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** The first instant of {@code from} in {@code zone}, the store's, included. */
    public Instant start(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    /** The first instant after {@code to} in {@code zone}, the store's, excluded. */
    public Instant end(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
