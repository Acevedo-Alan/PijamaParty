package com.pijamaparty.store.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityPeriodTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Test
    void givenTwoStoreDates_whenThePeriodIsBuilt_thenItRunsFromTheFirstMidnightToTheMidnightAfterTheLastDay() {
        ActivityPeriod period = ActivityPeriod.of(Optional.of(OCT_1), Optional.of(OCT_15)).orElseThrow();

        assertThat(period.start(ARGENTINA)).contains(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(period.end(ARGENTINA)).contains(Instant.parse("2026-10-16T03:00:00Z"));
    }

    @Test
    void givenTheSameDayTwice_whenThePeriodIsBuilt_thenItIsThatWholeDay() {
        ActivityPeriod period = ActivityPeriod.of(Optional.of(OCT_1), Optional.of(OCT_1)).orElseThrow();

        assertThat(period.start(ARGENTINA)).contains(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(period.end(ARGENTINA)).contains(Instant.parse("2026-10-02T03:00:00Z"));
    }

    @Test
    void givenFromAfterTo_whenThePeriodIsBuilt_thenThereIsNone() {
        assertThat(ActivityPeriod.of(Optional.of(OCT_15), Optional.of(OCT_1))).isEmpty();
    }

    @Test
    void givenNoDates_whenThePeriodIsBuilt_thenItHasNoBounds() {
        ActivityPeriod period = ActivityPeriod.of(Optional.empty(), Optional.empty()).orElseThrow();

        assertThat(period).isEqualTo(ActivityPeriod.ALWAYS);
        assertThat(period.start(ARGENTINA)).isEmpty();
        assertThat(period.end(ARGENTINA)).isEmpty();
    }

    @Test
    void givenOnlyOneDate_whenThePeriodIsBuilt_thenOnlyThatBoundIsSet() {
        assertThat(ActivityPeriod.of(Optional.of(OCT_1), Optional.empty()).orElseThrow().end(ARGENTINA)).isEmpty();
        assertThat(ActivityPeriod.of(Optional.empty(), Optional.of(OCT_1)).orElseThrow().start(ARGENTINA)).isEmpty();
    }
}
