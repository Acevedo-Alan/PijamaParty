package com.pijamaparty.store.ordering.adapter.out.event;

import com.pijamaparty.store.ordering.api.OrderEmailCorrected;
import com.pijamaparty.store.ordering.api.OrderPaid;
import com.pijamaparty.store.ordering.application.OrderEvents;
import com.pijamaparty.store.ordering.domain.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the events of {@code ordering.api} through Spring; plain listeners run at once, in the caller's thread. */
@Component
class SpringOrderEvents implements OrderEvents {

    private final ApplicationEventPublisher publisher;

    SpringOrderEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void orderPaid(OrderId order, long number) {
        publisher.publishEvent(new OrderPaid(order.value(), number));
    }

    @Override
    public void orderEmailCorrected(OrderId order, long number) {
        publisher.publishEvent(new OrderEmailCorrected(order.value(), number));
    }
}
