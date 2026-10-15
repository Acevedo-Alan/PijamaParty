package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnqueueWithdrawalEmailsService implements EnqueueWithdrawalEmails {

    private static final List<EmailType> WITHDRAWAL_RECEIVED =
        List.of(EmailType.WITHDRAWAL_RECEIVED_CUSTOMER, EmailType.WITHDRAWAL_RECEIVED_ADMIN);

    private final EnqueueEmails outbox;
    private final Clock clock;

    EnqueueWithdrawalEmailsService(EnqueueEmails outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Joins the request's transaction: no request, no email; no email, no request. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        Instant now = clock.instant();
        int enqueued = 0;
        for (EmailType type : WITHDRAWAL_RECEIVED) {
            if (outbox.enqueue(type, command.requestId(), now)) {
                enqueued++;
            }
        }
        return new Result(enqueued);
    }
}
