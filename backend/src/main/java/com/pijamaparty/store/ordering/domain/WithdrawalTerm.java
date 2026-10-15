package com.pijamaparty.store.ordering.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-29 (T-E3-25, decided): the legal term of a withdrawal, {@value #DAYS} calendar days from the delivery or the
 * purchase, whichever is later, counted in Argentina's time zone. A request out of term is still recorded, flagged.
 */
public final class WithdrawalTerm {

    public static final int DAYS = 10;

    static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private WithdrawalTerm() {
    }

    /**
     * Whether {@code now} is after the end of the {@value #DAYS}th day following the later of the purchase and the
     * delivery. An order not delivered yet is in term.
     */
    public static boolean isOutOfTerm(Instant purchasedAt, Optional<Instant> deliveredAt, Instant now) {
        Objects.requireNonNull(purchasedAt, "purchasedAt");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(now, "now");
        if (deliveredAt.isEmpty()) {
            return false;
        }
        Instant start = deliveredAt.get().isAfter(purchasedAt) ? deliveredAt.get() : purchasedAt;
        LocalDate lastDay = start.atZone(ARGENTINA).toLocalDate().plusDays(DAYS);
        return now.atZone(ARGENTINA).toLocalDate().isAfter(lastDay);
    }
}
