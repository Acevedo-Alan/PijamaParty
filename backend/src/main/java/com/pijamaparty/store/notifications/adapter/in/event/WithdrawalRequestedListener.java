package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.notifications.application.EnqueueWithdrawalEmails;
import com.pijamaparty.store.ordering.api.WithdrawalRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-29 (T-E3-25): a withdrawal request gets its emails. A plain (synchronous) listener, so they are enqueued in the
 * request's transaction, as the person that sent the form, and commit or roll back with it.
 */
@Component
class WithdrawalRequestedListener {

    private final EnqueueWithdrawalEmails enqueueWithdrawalEmails;

    WithdrawalRequestedListener(EnqueueWithdrawalEmails enqueueWithdrawalEmails) {
        this.enqueueWithdrawalEmails = enqueueWithdrawalEmails;
    }

    @EventListener
    void on(WithdrawalRequested event) {
        enqueueWithdrawalEmails.execute(new EnqueueWithdrawalEmails.Command(event.requestId()));
    }
}
