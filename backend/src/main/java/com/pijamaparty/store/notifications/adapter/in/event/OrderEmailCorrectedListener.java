package com.pijamaparty.store.notifications.adapter.in.event;

import com.pijamaparty.store.notifications.application.EnqueueCorrectedOrderEmail;
import com.pijamaparty.store.ordering.api.OrderEmailCorrected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-17 (T-E3-23): the buyer corrected the email of a paid order, so its confirmation goes again. A plain
 * (synchronous) listener, so it is enqueued in the correction's transaction, as the buyer, and commits or rolls back
 * with it (RNF-23).
 */
@Component
class OrderEmailCorrectedListener {

    private final EnqueueCorrectedOrderEmail enqueueCorrectedOrderEmail;

    OrderEmailCorrectedListener(EnqueueCorrectedOrderEmail enqueueCorrectedOrderEmail) {
        this.enqueueCorrectedOrderEmail = enqueueCorrectedOrderEmail;
    }

    @EventListener
    void on(OrderEmailCorrected event) {
        enqueueCorrectedOrderEmail.execute(new EnqueueCorrectedOrderEmail.Command(event.orderId()));
    }
}
