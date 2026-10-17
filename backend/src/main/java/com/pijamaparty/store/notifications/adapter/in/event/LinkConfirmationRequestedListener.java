package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.accounts.api.LinkConfirmationRequested;
import com.pijamaparty.store.notifications.application.EnqueueLinkConfirmation;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-27 (T-E4-14): a customer needs the link confirmation email. A plain (synchronous) listener, so it is enqueued in
 * the transaction that keeps the pending link, as {@code SYSTEM}, and commits or rolls back with it (RNF-23).
 */
@Component
class LinkConfirmationRequestedListener {

    private final EnqueueLinkConfirmation enqueueLinkConfirmation;

    LinkConfirmationRequestedListener(EnqueueLinkConfirmation enqueueLinkConfirmation) {
        this.enqueueLinkConfirmation = enqueueLinkConfirmation;
    }

    @EventListener
    void on(LinkConfirmationRequested event) {
        enqueueLinkConfirmation.execute(new EnqueueLinkConfirmation.Command(event.userId()));
    }
}
