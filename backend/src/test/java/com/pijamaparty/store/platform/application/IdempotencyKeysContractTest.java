package com.pijamaparty.store.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.application.IdempotencyKeys.Claim;
import com.pijamaparty.store.platform.application.IdempotencyKeys.StoredAnswer;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What every {@link IdempotencyKeys} and {@link ExpiredIdempotencyKeys} guarantees (T-E3-14, RNF-20). */
public abstract class IdempotencyKeysContractTest<T extends IdempotencyKeys & ExpiredIdempotencyKeys>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    protected static final UserId CUSTOMER_ID = UserId.of(new UUID(0xC, 1));
    protected static final CartToken GUEST_CART = new CartToken("a-guest-cart-token-for-keys");
    protected static final IdempotencyScope CUSTOMER =
        IdempotencyScope.of(Actor.customer(CUSTOMER_ID, Optional.empty(), Optional.empty())).orElseThrow();
    protected static final IdempotencyScope GUEST =
        IdempotencyScope.of(Actor.anonymous(Optional.of(GUEST_CART), Optional.empty())).orElseThrow();

    private static final StoredAnswer ANSWER = new StoredAnswer("a".repeat(64), UUID.randomUUID(), 201,
        Map.of("number", "1001", "totalCents", "4450000", "reservationExpiresAt", "2026-10-14T15:30:00Z"));

    @Test
    void givenAKeyNeverUsed_whenClaimingIt_thenItIsFresh() {
        assertThat(subject.claim(GUEST, UUID.randomUUID())).isEqualTo(new Claim.Fresh());
    }

    @Test
    void givenAnAnswerRecorded_whenTheKeyIsClaimedAgain_thenItAnswersTheSame() {
        UUID key = UUID.randomUUID();
        subject.claim(GUEST, key);
        subject.record(GUEST, key, ANSWER, NOW, NOW.plus(Duration.ofHours(24)));
        persistAndForget();

        assertThat(subject.claim(GUEST, key)).isEqualTo(new Claim.Answered(ANSWER));
    }

    @Test
    void givenAnAnswerRecordedInOneScope_whenTheSameKeyIsClaimedInAnother_thenItIsFresh() {
        UUID key = UUID.randomUUID();
        subject.record(GUEST, key, ANSWER, NOW, NOW.plus(Duration.ofHours(24)));
        persistAndForget();

        assertThat(subject.claim(CUSTOMER, key)).isEqualTo(new Claim.Fresh());
    }

    @Test
    void givenKeysBeforeAndAfterTheirExpiry_whenDeletingTheExpired_thenOnlyTheyGo() {
        UUID expired = UUID.randomUUID();
        UUID expiringNow = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        subject.record(GUEST, expired, ANSWER, NOW.minus(Duration.ofHours(25)), NOW.minus(Duration.ofHours(1)));
        subject.record(CUSTOMER, expiringNow, ANSWER, NOW.minus(Duration.ofHours(24)), NOW);
        subject.record(GUEST, alive, ANSWER, NOW.minus(Duration.ofHours(23)), NOW.plus(Duration.ofHours(1)));
        persistAndForget();

        assertThat(subject.deleteExpiredAt(NOW)).isEqualTo(2);

        assertThat(subject.claim(GUEST, expired)).isEqualTo(new Claim.Fresh());
        assertThat(subject.claim(CUSTOMER, expiringNow)).isEqualTo(new Claim.Fresh());
        assertThat(subject.claim(GUEST, alive)).isEqualTo(new Claim.Answered(ANSWER));
    }
}
