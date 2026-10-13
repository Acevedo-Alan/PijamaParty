package com.pijamaparty.store.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RetryScheduleTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:30Z");

    @ParameterizedTest(name = "attempt {0}: {1} minutes later")
    @CsvSource({"1, 1", "2, 5", "3, 15", "4, 60", "5, 60"})
    void givenAnAttempt_whenItBegins_thenTheNextIsAt1_5_15And60MinutesAfter(int attempt, long minutes) {
        assertThat(RetrySchedule.nextAttemptAt(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(minutes)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void givenAnAttemptOutOfRange_whenScheduled_thenItFails(int attempt) {
        assertThatThrownBy(() -> RetrySchedule.nextAttemptAt(attempt, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTheSchedule_whenAsked_thenAnEmailHasFiveAttempts() {
        assertThat(RetrySchedule.MAX_ATTEMPTS).isEqualTo(5);
    }
}
