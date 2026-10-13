package com.pijamaparty.store.notifications.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An email of the outbox still to send (RNF-23). It holds no personal data: what it says, and to whom, is composed
 * from its reference when it is sent.
 *
 * @param attempts the attempts already begun, from 0 to {@link RetrySchedule#MAX_ATTEMPTS}
 */
public record OutboxEmail(UUID id, EmailType type, UUID reference, int attempts) {

    public OutboxEmail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reference, "reference");
        if (attempts < 0 || attempts > RetrySchedule.MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempts out of range: " + attempts);
        }
    }

    /**
     * Takes the email to send it now: one more attempt, retried later if this one does not end (a stopped process,
     * a provider that does not answer). An email whose attempts are over is not sent again.
     */
    public Claim claim(Instant now) {
        if (attempts == RetrySchedule.MAX_ATTEMPTS) {
            return new Claim.Exhausted(this);
        }
        int attempt = attempts + 1;
        return new Claim.Attempt(new OutboxEmail(id, type, reference, attempt),
            RetrySchedule.nextAttemptAt(attempt, now));
    }

    /** No attempt is left after this one. */
    public boolean isLastAttempt() {
        return attempts == RetrySchedule.MAX_ATTEMPTS;
    }

    public sealed interface Claim {

        OutboxEmail email();

        /** Send it now; if it is still pending at {@code retryAt}, it is tried again. */
        record Attempt(OutboxEmail email, Instant retryAt) implements Claim {

            public Attempt {
                Objects.requireNonNull(email, "email");
                Objects.requireNonNull(retryAt, "retryAt");
            }
        }

        /** All its attempts are spent: it failed. */
        record Exhausted(OutboxEmail email) implements Claim {

            public Exhausted {
                Objects.requireNonNull(email, "email");
            }
        }
    }
}
