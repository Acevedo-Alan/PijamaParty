package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnqueueEmailVerificationService implements EnqueueEmailVerification {

    private final ResendEmails outbox;
    private final Clock clock;

    EnqueueEmailVerificationService(ResendEmails outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Joins the registration's or the sign-in's transaction: no account, no email. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        // Locked, the job cannot take the waiting one before this transaction ends; one being sent is waited for.
        if (outbox.lockUnattempted(EmailType.EMAIL_VERIFICATION, command.userId())) {
            return new Result.PendingTakesIt();
        }
        return new Result.Enqueued(
            outbox.enqueueNextVersion(EmailType.EMAIL_VERIFICATION, command.userId(), clock.instant()));
    }
}
