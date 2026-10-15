package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.CorrectableOrders.CorrectableOrder;
import com.pijamaparty.store.ordering.domain.BuyerEmail;
import com.pijamaparty.store.ordering.domain.EmailCorrection;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-17 (T-E3-23). One transaction, as the buyer: the order stays locked from reading its status until the corrected
 * email and, on a paid order, the confirmation enqueued again by the listeners of {@code OrderEmailCorrected} commit
 * together.
 */
@Service
class CorrectOrderEmailService implements CorrectOrderEmail {

    private final CorrectableOrders orders;
    private final BuyerEmails emails;
    private final OrderEvents events;
    private final Clock clock;

    CorrectOrderEmailService(CorrectableOrders orders, BuyerEmails emails, OrderEvents events, Clock clock) {
        this.orders = orders;
        this.emails = emails;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<String> email = BuyerEmail.normalize(command.email());
        if (email.isEmpty()) {
            return new Result.InvalidEmail();
        }
        Optional<CorrectableOrder> order = orders.lock(command.token());
        if (order.isEmpty()) {
            return new Result.NotFound();
        }
        EmailCorrection correction = EmailCorrection.of(order.get().status());
        if (correction == EmailCorrection.CLOSED) {
            return new Result.Closed(order.get().status());
        }
        // The order is locked and the actor reached it, so the update applies.
        if (!emails.replace(order.get().id(), email.get(), clock.instant())) {
            throw new IllegalStateException("order " + order.get().id() + " vanished while locked");
        }
        boolean resend = correction == EmailCorrection.RESEND_CONFIRMATION;
        if (resend) {
            events.orderEmailCorrected(order.get().id(), order.get().number());
        }
        return new Result.Corrected(resend);
    }
}
