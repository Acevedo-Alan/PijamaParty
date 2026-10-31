package com.pijamaparty.store.backoffice.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * UC-33: the periods of the summary from today's Argentina date: today against yesterday, the last 7 days (today and
 * the 6 before) against the 7 before those, and this month (the 1st to today) against the same days of the previous
 * month (the 1st up to today's day number, or that month's last day if it is shorter); and the chart's 30 days and 12
 * months, today and this month included.
 */
public final class SalesPeriods {

    /** The store's dates (R-07). */
    public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    static final int WEEK_DAYS = 7;
    static final int CHART_DAYS = 30;
    static final int CHART_MONTHS = 12;

    private final LocalDate today;

    private SalesPeriods(LocalDate today) {
        this.today = Objects.requireNonNull(today, "today");
    }

    public static SalesPeriods of(LocalDate today) {
        return new SalesPeriods(today);
    }

    public DateRange today() {
        return new DateRange(today, today);
    }

    public DateRange yesterday() {
        LocalDate day = today.minusDays(1);
        return new DateRange(day, day);
    }

    public DateRange lastWeek() {
        return new DateRange(today.minusDays(WEEK_DAYS - 1L), today);
    }

    public DateRange weekBefore() {
        return new DateRange(today.minusDays(2L * WEEK_DAYS - 1), today.minusDays(WEEK_DAYS));
    }

    public DateRange month() {
        return new DateRange(today.withDayOfMonth(1), today);
    }

    public DateRange monthBefore() {
        YearMonth previous = YearMonth.from(today).minusMonths(1);
        return new DateRange(previous.atDay(1), previous.atDay(Math.min(today.getDayOfMonth(), previous.lengthOfMonth())));
    }

    /** The chart by days: the last 30, today included. */
    public DateRange chartDays() {
        return new DateRange(today.minusDays(CHART_DAYS - 1L), today);
    }

    /** The chart by months: the last 12, this month included, from the 1st of the first one. */
    public DateRange chartMonths() {
        return new DateRange(YearMonth.from(today).minusMonths(CHART_MONTHS - 1L).atDay(1), today);
    }
}
