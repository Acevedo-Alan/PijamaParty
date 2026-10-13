package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail.Claim;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimNextEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:30Z");

    private final InMemoryEmailOutbox outbox = new InMemoryEmailOutbox();
    private final UUID order = UUID.randomUUID();

    @Test
    void givenNoEmailDue_whenClaimed_thenThereIsNone() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW.plusSeconds(1));

        assertThat(claimAt(NOW)).isEmpty();
    }

    @Test
    void givenAnEmailDue_whenClaimed_thenItIsTheFirstAttemptAndWaitsAMinuteBeforeAnother() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);

        Optional<Claim> claim = claimAt(NOW);

        assertThat(claim).hasValueSatisfying(taken -> {
            assertThat(taken).isInstanceOf(Claim.Attempt.class);
            assertThat(taken.email().reference()).isEqualTo(order);
            assertThat(taken.email().attempts()).isOne();
            assertThat(((Claim.Attempt) taken).retryAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        });
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("PENDING");
            assertThat(email.attempts()).isOne();
            assertThat(email.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        });
        assertThat(claimAt(NOW.plusSeconds(59))).isEmpty();
    }

    @Test
    void givenAnEmailNeverSent_whenClaimedAtEachRetry_thenItWaits1_5_15And60MinutesThenFails() {
        outbox.enqueue(EmailType.ORDER_PAID_CUSTOMER, order, NOW);
        Instant at = NOW;
        for (long minutes : new long[] {1, 5, 15, 60, 60}) {
            Claim claim = claimAt(at).orElseThrow();
            assertThat(claim).isInstanceOf(Claim.Attempt.class);
            assertThat(((Claim.Attempt) claim).retryAt()).isEqualTo(at.plus(Duration.ofMinutes(minutes)));
            at = at.plus(Duration.ofMinutes(minutes));
        }

        Optional<Claim> exhausted = claimAt(at);

        assertThat(exhausted).hasValueSatisfying(claim -> assertThat(claim).isInstanceOf(Claim.Exhausted.class));
        assertThat(outbox.find(EmailType.ORDER_PAID_CUSTOMER, order)).hasValueSatisfying(email -> {
            assertThat(email.status()).isEqualTo("FAILED");
            assertThat(email.attempts()).isEqualTo(5);
        });
        assertThat(claimAt(at.plus(Duration.ofDays(1)))).isEmpty();
    }

    private Optional<Claim> claimAt(Instant at) {
        return new ClaimNextEmailService(outbox, outbox, Clock.fixed(at, ZoneOffset.UTC)).execute();
    }
}
