package com.pijamaparty.store.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ActivityRetentionTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void givenNow_whenTheCutoffIsComputed_thenItIsTheSameMomentTwoYearsEarlier() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 20, 3, 0, 0, 0, ARGENTINA);

        assertThat(ActivityRetention.cutoff(now)).isEqualTo(Instant.parse("2024-10-20T06:00:00Z"));
    }

    @Test
    void givenFebruary29_whenTheCutoffIsComputed_thenItFallsOnFebruary28TwoYearsEarlier() {
        ZonedDateTime now = ZonedDateTime.of(2028, 2, 29, 3, 0, 0, 0, ARGENTINA);

        assertThat(ActivityRetention.cutoff(now)).isEqualTo(Instant.parse("2026-02-28T06:00:00Z"));
    }
}
