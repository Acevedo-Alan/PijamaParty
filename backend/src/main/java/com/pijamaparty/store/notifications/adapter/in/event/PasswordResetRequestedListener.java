package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.accounts.api.PasswordResetRequested;
import com.pijamaparty.store.notifications.application.EnqueuePasswordReset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-19 (T-E4-03): a customer asked to recover its password. A plain (synchronous) listener, so the reset email is
 * enqueued in the recovery request's transaction, as {@code SYSTEM}, and commits or rolls back with it (RNF-23).
 */
@Component
class PasswordResetRequestedListener {

    private final EnqueuePasswordReset enqueuePasswordReset;

    PasswordResetRequestedListener(EnqueuePasswordReset enqueuePasswordReset) {
        this.enqueuePasswordReset = enqueuePasswordReset;
    }

    @EventListener
    void on(PasswordResetRequested event) {
        enqueuePasswordReset.execute(new EnqueuePasswordReset.Command(event.userId()));
    }
}
