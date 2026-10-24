package com.pijamaparty.store.activity.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-37: the date filter of the "Actividad" screen. Both dates are optional and inclusive, store dates (R-07): a day
 * runs from 00:00 to 24:00 in the store's zone.
 */
public record ActivityPeriod(Optional<LocalDate> from, Optional<LocalDate> to) {

    public static final ActivityPeriod ALWAYS = new ActivityPeriod(Optional.empty(), Optional.empty());

    public ActivityPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    /** Empty when {@code from} is after {@code to}. */
    public static Optional<ActivityPeriod> of(Optional<LocalDate> from, Optional<LocalDate> to) {
        if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
            return Optional.empty();
        }
        return Optional.of(new ActivityPeriod(from, to));
    }

    /** The first instant of {@code from} in {@code zone}. */
    public Optional<Instant> start(ZoneId zone) {
        return from.map(date -> date.atStartOfDay(zone).toInstant());
    }

    /** The first instant after {@code to} in {@code zone}: entries before it are inside. */
    public Optional<Instant> end(ZoneId zone) {
        return to.map(date -> date.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
