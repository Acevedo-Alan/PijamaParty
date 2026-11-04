package com.pijamaparty.store.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * UC-34 (T-E8-10): the registration filter of the customer list. Both dates are optional and inclusive, store dates
 * (R-07): a day runs from 00:00 to 24:00 in the store's zone.
 */
public record RegistrationPeriod(Optional<LocalDate> from, Optional<LocalDate> to) {

    /** The dates a filter may name: far around the store's life, and always inside what PostgreSQL stores. */
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    public static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    public RegistrationPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!valid(from, to)) {
            throw new IllegalArgumentException("from is after to, or a date is out of range");
        }
    }

    /** Empty when {@code from} is after {@code to}, or a date is before {@link #EARLIEST} or after {@link #LATEST}. */
    public static Optional<RegistrationPeriod> of(Optional<LocalDate> from, Optional<LocalDate> to) {
        return valid(from, to) ? Optional.of(new RegistrationPeriod(from, to)) : Optional.empty();
    }

    private static boolean valid(Optional<LocalDate> from, Optional<LocalDate> to) {
        boolean inRange = Stream.concat(from.stream(), to.stream())
            .allMatch(date -> !date.isBefore(EARLIEST) && !date.isAfter(LATEST));
        return inRange && (from.isEmpty() || to.isEmpty() || !from.get().isAfter(to.get()));
    }

    /** The first instant of {@code from} in {@code zone}. */
    public Optional<Instant> start(ZoneId zone) {
        return from.map(date -> date.atStartOfDay(zone).toInstant());
    }

    /** The first instant after {@code to} in {@code zone}: customers registered before it are inside. */
    public Optional<Instant> end(ZoneId zone) {
        return to.map(date -> date.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
