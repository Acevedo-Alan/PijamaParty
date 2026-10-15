package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnqueueCorrectedOrderEmailService implements EnqueueCorrectedOrderEmail {

    private final ResendEmails outbox;
    private final Clock clock;

    EnqueueCorrectedOrderEmailService(ResendEmails outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Joins the correction's transaction: the email and its confirmation commit or roll back together. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        // Every email is composed from the order when sent, so one not attempted yet already takes the new email.
        // Locked, the job cannot compose it before the correction commits.
        if (outbox.lockUnattempted(EmailType.ORDER_PAID_CUSTOMER, command.orderId())) {
            return new Result.PendingTakesIt();
        }
        return new Result.Enqueued(
            outbox.enqueueNextVersion(EmailType.ORDER_PAID_CUSTOMER, command.orderId(), clock.instant()));
    }
}
