package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnqueueLinkConfirmationService implements EnqueueLinkConfirmation {

    private final ResendEmails outbox;
    private final Clock clock;

    EnqueueLinkConfirmationService(ResendEmails outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Joins the transaction that keeps the pending link: no request, no email. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        // Locked, the job cannot take the waiting one before this transaction ends; one being sent is waited for.
        if (outbox.lockUnattempted(EmailType.LINK_CONFIRMATION, command.userId())) {
            return new Result.PendingTakesIt();
        }
        return new Result.Enqueued(
            outbox.enqueueNextVersion(EmailType.LINK_CONFIRMATION, command.userId(), clock.instant()));
    }
}
