package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.notifications.application.EnqueueEmailVerification;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-19 (T-E4-02): a customer needs a verification email. A plain (synchronous) listener, so it is enqueued in the
 * registration's or the sign-in's transaction, as {@code SYSTEM}, and commits or rolls back with it (RNF-23).
 */
@Component
class EmailVerificationRequestedListener {

    private final EnqueueEmailVerification enqueueEmailVerification;

    EmailVerificationRequestedListener(EnqueueEmailVerification enqueueEmailVerification) {
        this.enqueueEmailVerification = enqueueEmailVerification;
    }

    @EventListener
    void on(EmailVerificationRequested event) {
        enqueueEmailVerification.execute(new EnqueueEmailVerification.Command(event.userId()));
    }
}
