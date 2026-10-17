package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.accounts.api.LinkAttemptNoticeRequested;
import com.pijamaparty.store.notifications.application.EnqueueLinkAttemptNotice;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-27 (T-E4-14): a customer needs the link attempt notice. A plain (synchronous) listener, so it is enqueued in the
 * transaction of the request, as {@code SYSTEM}, and commits or rolls back with it (RNF-23).
 */
@Component
class LinkAttemptNoticeRequestedListener {

    private final EnqueueLinkAttemptNotice enqueueLinkAttemptNotice;

    LinkAttemptNoticeRequestedListener(EnqueueLinkAttemptNotice enqueueLinkAttemptNotice) {
        this.enqueueLinkAttemptNotice = enqueueLinkAttemptNotice;
    }

    @EventListener
    void on(LinkAttemptNoticeRequested event) {
        enqueueLinkAttemptNotice.execute(new EnqueueLinkAttemptNotice.Command(event.userId()));
    }
}
