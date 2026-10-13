package com.pijamaparty.store.payments.adapter.out.event;

import com.pijamaparty.store.payments.api.PaymentApproved;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentEvents;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the events of {@code payments.api} through Spring; plain listeners run at once, in the caller's thread. */
@Component
class SpringPaymentEvents implements PaymentEvents {

    private final ApplicationEventPublisher publisher;

    SpringPaymentEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void paymentApproved(UUID orderId, PaymentProvider provider) {
        publisher.publishEvent(new PaymentApproved(orderId, provider));
    }
}
