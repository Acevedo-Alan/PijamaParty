package com.pijamaparty.store.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimeConfigurationTest {

    @Test
    void givenTheApplicationClock_whenReadingItsZone_thenItIsBuenosAires() {
        Clock clock = new TimeConfiguration().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void givenAUseCaseThatNeedsTheCurrentTime_whenItIsTested_thenTheTestControlsTimeThroughTheInjectedClock() {
        // 02:30 UTC on October 10 is still October 9 in Buenos Aires (UTC-3)
        Clock fixed = Clock.fixed(Instant.parse("2026-10-10T02:30:00Z"), TimeConfiguration.STORE_ZONE);
        var useCase = new TodayInTheStore(fixed);

        assertThat(useCase.today()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    /** Stand-in for any use case: it receives the Clock by constructor and never calls {@code now()} without it. */
    private record TodayInTheStore(Clock clock) {
        LocalDate today() {
            return LocalDate.now(clock);
        }
    }
}
