package com.pijamaparty.store.backoffice.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UC-33: the chart's bars. Days or months without sales are 0, not gaps: the queries return only the ones with sales,
 * and the series fills the rest here.
 */
public final class SalesSeries {

    private SalesSeries() {
    }

    /** One bar: a day ({@code 2026-10-31}) or a month ({@code 2026-10}). */
    public record Point(String period, Money amount, long orders) {

        public Point {
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(amount, "amount");
        }
    }

    /** Every day of {@code range}, oldest first. */
    public static List<Point> days(DateRange range, Map<LocalDate, SalesTotal> sold) {
        List<Point> points = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            SalesTotal total = sold.getOrDefault(day, SalesTotal.NONE);
            points.add(new Point(day.toString(), total.amount(), total.orders()));
        }
        return List.copyOf(points);
    }

    /** Every month that {@code range} touches, oldest first. */
    public static List<Point> months(DateRange range, Map<YearMonth, SalesTotal> sold) {
        List<Point> points = new ArrayList<>();
        YearMonth last = YearMonth.from(range.to());
        for (YearMonth month = YearMonth.from(range.from()); !month.isAfter(last); month = month.plusMonths(1)) {
            SalesTotal total = sold.getOrDefault(month, SalesTotal.NONE);
            points.add(new Point(month.toString(), total.amount(), total.orders()));
        }
        return List.copyOf(points);
    }
}
