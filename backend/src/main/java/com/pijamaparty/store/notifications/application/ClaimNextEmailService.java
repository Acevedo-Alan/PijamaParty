package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.OutboxEmail.Claim;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClaimNextEmailService implements ClaimNextEmail {

    private final LockDueEmails dueEmails;
    private final RecordEmailOutcomes outcomes;
    private final Clock clock;

    ClaimNextEmailService(LockDueEmails dueEmails, RecordEmailOutcomes outcomes, Clock clock) {
        this.dueEmails = dueEmails;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    /** Locks the email, lets the domain claim it, and stores the claim, all in one short transaction. */
    @Override
    @Transactional
    public Optional<Claim> execute() {
        Instant now = clock.instant();
        return dueEmails.lockNextDue(now).map(email -> {
            Claim claim = email.claim(now);
            switch (claim) {
                case Claim.Attempt attempt ->
                    outcomes.attempting(email.id(), attempt.email().attempts(), attempt.retryAt(), now);
                case Claim.Exhausted exhausted -> outcomes.failed(email.id(), now);
            }
            return claim;
        });
    }
}
