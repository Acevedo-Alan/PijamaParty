package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.SalesReadings.VariantSold;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link SalesReadings} (UC-33): a range of Argentina dates includes both its days, from the first one's
 * midnight to the midnight after the last; only orders that count as sales add up.
 */
public abstract class SalesReadingsContractTest<T extends SalesReadings> extends ContractTest<T> {

    /** An order that counts as a sale (or not), created at {@code createdAt}, of one line of {@code variant}. */
    protected abstract void givenOrder(boolean sale, Instant createdAt, long totalCents, UUID variant, int units,
                                       long unitCents);

    private static DateRange days(String from, String to) {
        return new DateRange(LocalDate.parse(from), LocalDate.parse(to));
    }

    @Test
    void givenOrdersAtTheEdgesOfArgentinaDays_whenARangeIsTotalled_thenBothOfItsDaysAreIn() {
        UUID variant = UUID.randomUUID();
        givenOrder(true, Instant.parse("2026-10-14T03:00:00Z"), 1_000_000, variant, 1, 1_000_000);
        givenOrder(true, Instant.parse("2026-10-16T02:59:00Z"), 2_000_000, variant, 1, 2_000_000);
        givenOrder(true, Instant.parse("2026-10-16T03:00:00Z"), 4_000_000, variant, 1, 4_000_000);
        givenOrder(false, Instant.parse("2026-10-15T15:00:00Z"), 8_000_000, variant, 1, 8_000_000);
        persistAndForget();

        assertThat(subject.total(days("2026-10-14", "2026-10-15"))).isEqualTo(new SalesTotal(Money.of(3_000_000), 2));
    }

    @Test
    void givenSalesOnTwoDays_whenGroupedByDayAndByMonth_thenEachDayAndMonthHasItsOwn() {
        UUID variant = UUID.randomUUID();
        givenOrder(true, Instant.parse("2026-09-30T15:00:00Z"), 1_000_000, variant, 1, 1_000_000);
        givenOrder(true, Instant.parse("2026-10-15T15:00:00Z"), 2_000_000, variant, 1, 2_000_000);
        givenOrder(true, Instant.parse("2026-10-15T16:00:00Z"), 3_000_000, variant, 1, 3_000_000);
        persistAndForget();

        assertThat(subject.perDay(days("2026-09-01", "2026-10-31"))).isEqualTo(Map.of(
            LocalDate.parse("2026-09-30"), new SalesTotal(Money.of(1_000_000), 1),
            LocalDate.parse("2026-10-15"), new SalesTotal(Money.of(5_000_000), 2)));
        assertThat(subject.perMonth(days("2026-09-01", "2026-10-31"))).isEqualTo(Map.of(
            YearMonth.parse("2026-09"), new SalesTotal(Money.of(1_000_000), 1),
            YearMonth.parse("2026-10"), new SalesTotal(Money.of(5_000_000), 2)));
    }

    @Test
    void givenAVariantSoldTwice_whenGroupedByVariant_thenItsUnitsAndAmountAddUp() {
        UUID variant = UUID.randomUUID();
        givenOrder(true, Instant.parse("2026-10-15T15:00:00Z"), 6_000_000, variant, 3, 2_000_000);
        givenOrder(true, Instant.parse("2026-10-15T16:00:00Z"), 4_000_000, variant, 2, 2_000_000);
        persistAndForget();

        assertThat(subject.byVariant(days("2026-10-15", "2026-10-15")))
            .containsExactly(new VariantSold(variant, 5, Money.of(10_000_000)));
    }
}
