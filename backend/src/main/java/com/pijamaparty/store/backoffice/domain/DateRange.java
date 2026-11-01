package com.pijamaparty.store.backoffice.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Argentina dates from {@code from} to {@code to}, both included (R-07). */
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

    /** The first instant of {@code from} in Argentina, included. */
    public Instant start() {
        return from.atStartOfDay(SalesPeriods.ARGENTINA).toInstant();
    }

    /** The first instant after {@code to} in Argentina, excluded. */
    public Instant end() {
        return to.plusDays(1).atStartOfDay(SalesPeriods.ARGENTINA).toInstant();
    }
}
