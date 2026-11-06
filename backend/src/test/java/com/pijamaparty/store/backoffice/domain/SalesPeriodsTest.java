package com.pijamaparty.store.backoffice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SalesPeriodsTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static DateRange range(String from, String to) {
        return new DateRange(LocalDate.parse(from), LocalDate.parse(to));
    }

    @Test
    void givenAnOrdinaryDay_whenThePeriodsAreComputed_thenEachPeriodHasItsComparison() {
        SalesPeriods periods = SalesPeriods.of(LocalDate.parse("2026-10-15"));

        assertThat(periods.today()).isEqualTo(range("2026-10-15", "2026-10-15"));
        assertThat(periods.yesterday()).isEqualTo(range("2026-10-14", "2026-10-14"));
        assertThat(periods.lastWeek()).isEqualTo(range("2026-10-09", "2026-10-15"));
        assertThat(periods.weekBefore()).isEqualTo(range("2026-10-02", "2026-10-08"));
        assertThat(periods.month()).isEqualTo(range("2026-10-01", "2026-10-15"));
        assertThat(periods.monthBefore()).isEqualTo(range("2026-09-01", "2026-09-15"));
        assertThat(periods.chartDays()).isEqualTo(range("2026-09-16", "2026-10-15"));
        assertThat(periods.chartMonths()).isEqualTo(range("2025-11-01", "2026-10-15"));
    }

    @Test
    void givenThe31st_whenTheMonthBeforeHas30Days_thenItsComparisonEndsOnItsLastDay() {
        assertThat(SalesPeriods.of(LocalDate.parse("2026-10-31")).monthBefore())
            .isEqualTo(range("2026-09-01", "2026-09-30"));
    }

    @Test
    void givenMarch30_whenTheMonthBeforeIsFebruary_thenALeapYearEndsOn29AndOthersOn28() {
        assertThat(SalesPeriods.of(LocalDate.parse("2028-03-30")).monthBefore())
            .isEqualTo(range("2028-02-01", "2028-02-29"));
        assertThat(SalesPeriods.of(LocalDate.parse("2027-03-30")).monthBefore())
            .isEqualTo(range("2027-02-01", "2027-02-28"));
    }

    @Test
    void givenJanuaryFirst_whenThePeriodsAreComputed_thenTheyCrossIntoTheYearBefore() {
        SalesPeriods periods = SalesPeriods.of(LocalDate.parse("2027-01-01"));

        assertThat(periods.yesterday()).isEqualTo(range("2026-12-31", "2026-12-31"));
        assertThat(periods.monthBefore()).isEqualTo(range("2026-12-01", "2026-12-01"));
        assertThat(periods.chartMonths().from()).isEqualTo(LocalDate.parse("2026-02-01"));
    }

    @Test
    void givenAnArgentinaDate_whenItsInstantsAreAsked_thenTheDayRunsFromMidnightToMidnightThere() {
        DateRange day = range("2026-10-15", "2026-10-15");

        assertThat(day.start(ARGENTINA)).isEqualTo(Instant.parse("2026-10-15T03:00:00Z"));
        assertThat(day.end(ARGENTINA)).isEqualTo(Instant.parse("2026-10-16T03:00:00Z"));
    }

    @Test
    void givenSalesBeforeAndNow_whenTheVariationIsComputed_thenItIsAWholePercentOrNone() {
        assertThat(Variation.percent(Money.of(15_000_000), Money.of(10_000_000))).hasValue(50);
        assertThat(Variation.percent(Money.of(8_000_000), Money.of(10_000_000))).hasValue(-20);
        assertThat(Variation.percent(Money.of(1_005), Money.of(1_000))).hasValue(1);
        assertThat(Variation.percent(Money.of(5_000), Money.zero())).isEmpty();
    }

    @Test
    void givenAMonthsSales_whenTheAverageTicketIsComputed_thenItIsRoundedDownToTheCentOrNone() {
        assertThat(new SalesTotal(Money.of(1_000_000), 3).averageTicket()).contains(Money.of(333_333));
        assertThat(SalesTotal.NONE.averageTicket()).isEqualTo(Optional.empty());
    }

    @Test
    void givenADayWithoutSales_whenTheChartIsFilled_thenThatDayIsAZeroBar() {
        var sold = Map.of(LocalDate.parse("2026-10-13"), new SalesTotal(Money.of(500_000), 2));

        assertThat(SalesSeries.days(range("2026-10-13", "2026-10-15"), sold)).containsExactly(
            new SalesSeries.Point("2026-10-13", Money.of(500_000), 2),
            new SalesSeries.Point("2026-10-14", Money.zero(), 0),
            new SalesSeries.Point("2026-10-15", Money.zero(), 0));
    }

    @Test
    void givenMonthsWithoutSales_whenTheChartIsFilledByMonths_thenEveryMonthOfTheRangeShows() {
        var sold = Map.of(YearMonth.parse("2026-09"), new SalesTotal(Money.of(700_000), 1));

        assertThat(SalesSeries.months(range("2026-08-01", "2026-10-15"), sold))
            .extracting(SalesSeries.Point::period, point -> point.amount().cents())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("2026-08", 0L),
                org.assertj.core.groups.Tuple.tuple("2026-09", 700_000L),
                org.assertj.core.groups.Tuple.tuple("2026-10", 0L));
    }

    @Test
    void givenOctober1To3_whenItIsACustomPeriod_thenItIsComparedAgainstSeptember28To30() {
        SalesPeriods periods = SalesPeriods.of(LocalDate.parse("2026-10-15"));

        DateRange custom = periods.custom(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03")).orElseThrow();

        assertThat(custom).isEqualTo(range("2026-10-01", "2026-10-03"));
        assertThat(SalesPeriods.before(custom)).isEqualTo(range("2026-09-28", "2026-09-30"));
    }

    @Test
    void givenACustomPeriod_whenItIsBackwardsInTheFutureOrLongerThan366Days_thenItIsNotOne() {
        SalesPeriods periods = SalesPeriods.of(LocalDate.parse("2026-10-15"));

        assertThat(periods.custom(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-04"))).isEmpty();
        assertThat(periods.custom(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-16"))).isEmpty();
        assertThat(periods.custom(LocalDate.parse("2025-10-14"), LocalDate.parse("2026-10-15"))).isEmpty();
        assertThat(periods.custom(LocalDate.parse("2025-10-15"), LocalDate.parse("2026-10-15"))).isPresent();
        assertThat(periods.custom(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-15"))).isPresent();
    }
}
