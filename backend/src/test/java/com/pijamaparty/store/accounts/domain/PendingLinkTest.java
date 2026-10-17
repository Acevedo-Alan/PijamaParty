package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): the first request for a customer's link wins while it is fresh. */
class PendingLinkTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
    private static final ExternalAccount OTHER = new ExternalAccount(ExternalProvider.FACEBOOK, "999");

    private final PendingLink pending = new PendingLink(UserId.of(UUID.randomUUID()), FACEBOOK, NOW);

    @Test
    void givenAFreshPendingLink_whenTheSameExternalAccountAsksAgain_thenItYields() {
        assertThat(pending.yieldsTo(FACEBOOK, NOW.plus(Duration.ofMinutes(5)))).isTrue();
    }

    @Test
    void givenAFreshPendingLink_whenAnotherExternalAccountAsks_thenItHolds() {
        assertThat(pending.yieldsTo(OTHER, NOW)).isFalse();
        assertThat(pending.yieldsTo(OTHER, NOW.plus(Duration.ofHours(24)).minusNanos(1000))).isFalse();
    }

    @Test
    void givenAPendingLink24HoursOld_whenAnotherExternalAccountAsks_thenItYields() {
        assertThat(pending.yieldsTo(OTHER, NOW.plus(Duration.ofHours(24)))).isTrue();
    }
}
