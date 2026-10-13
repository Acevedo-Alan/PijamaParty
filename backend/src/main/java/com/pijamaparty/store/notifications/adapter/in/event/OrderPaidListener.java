package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.notifications.application.EnqueueOrderPaidEmails;
import com.pijamaparty.store.ordering.api.OrderPaid;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-17: a paid order gets its payment-confirmed emails. A plain (synchronous) listener, so they are enqueued in the
 * payment's transaction, as {@code SYSTEM}, and commit or roll back with it (RNF-23).
 */
@Component
class OrderPaidListener {

    private final EnqueueOrderPaidEmails enqueueOrderPaidEmails;

    OrderPaidListener(EnqueueOrderPaidEmails enqueueOrderPaidEmails) {
        this.enqueueOrderPaidEmails = enqueueOrderPaidEmails;
    }

    @EventListener
    void on(OrderPaid event) {
        enqueueOrderPaidEmails.execute(new EnqueueOrderPaidEmails.Command(event.orderId()));
    }
}
