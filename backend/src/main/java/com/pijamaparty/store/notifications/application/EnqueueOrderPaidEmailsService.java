package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnqueueOrderPaidEmailsService implements EnqueueOrderPaidEmails {

    private static final List<EmailType> ORDER_PAID =
        List.of(EmailType.ORDER_PAID_CUSTOMER, EmailType.ORDER_PAID_ADMIN);

    private final EnqueueEmails outbox;
    private final Clock clock;

    EnqueueOrderPaidEmailsService(EnqueueEmails outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Joins the payment's transaction: no payment, no email; no email, no payment. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        Instant now = clock.instant();
        int enqueued = 0;
        for (EmailType type : ORDER_PAID) {
            if (outbox.enqueue(type, command.orderId(), now)) {
                enqueued++;
            }
        }
        return new Result(enqueued);
    }
}
