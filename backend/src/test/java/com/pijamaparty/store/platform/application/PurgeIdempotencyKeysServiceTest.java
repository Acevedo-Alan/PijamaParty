package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.application.IdempotencyKeys.StoredAnswer;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-E3-14 (RNF-20.2): {@link PurgeIdempotencyKeys}. */
class PurgeIdempotencyKeysServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T07:30:00Z");
    private static final IdempotencyScope GUEST =
        IdempotencyScope.of(Actor.anonymous(Optional.of(new CartToken("a-guest-cart-token")), Optional.empty()))
            .orElseThrow();
    private static final StoredAnswer ANSWER = new StoredAnswer("b".repeat(64), UUID.randomUUID(), 201, Map.of());

    private final InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
    private final PurgeIdempotencyKeysService purge =
        new PurgeIdempotencyKeysService(keys, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenKeysPastTheir24Hours_whenThePurgeRuns_thenTheyAreDeletedAndTheOthersKept() {
        keys.record(GUEST, UUID.randomUUID(), ANSWER, NOW.minus(Duration.ofHours(30)), NOW.minus(Duration.ofHours(6)));
        keys.record(GUEST, UUID.randomUUID(), ANSWER, NOW.minus(Duration.ofHours(24)), NOW);
        keys.record(GUEST, UUID.randomUUID(), ANSWER, NOW.minus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(22)));

        assertThat(purge.execute()).isEqualTo(new PurgeIdempotencyKeys.Result(2));
        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    void givenNoExpiredKey_whenThePurgeRuns_thenNothingIsDeleted() {
        keys.record(GUEST, UUID.randomUUID(), ANSWER, NOW, NOW.plus(Duration.ofHours(24)));

        assertThat(purge.execute()).isEqualTo(new PurgeIdempotencyKeys.Result(0));
        assertThat(keys.size()).isEqualTo(1);
    }
}
