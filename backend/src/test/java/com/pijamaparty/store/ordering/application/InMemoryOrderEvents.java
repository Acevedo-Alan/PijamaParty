package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderEmailCorrected;
import com.pijamaparty.store.ordering.api.OrderPaid;
import com.pijamaparty.store.ordering.api.WithdrawalRequested;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link OrderEvents}: records what it publishes. */
public class InMemoryOrderEvents implements OrderEvents {

    private final List<Object> published = new CopyOnWriteArrayList<>();

    @Override
    public void orderPaid(OrderId order, long number) {
        published.add(new OrderPaid(order.value(), number));
    }

    @Override
    public void orderEmailCorrected(OrderId order, long number) {
        published.add(new OrderEmailCorrected(order.value(), number));
    }

    @Override
    public void withdrawalRequested(UUID request, OrderId order, long number) {
        published.add(new WithdrawalRequested(request, order.value(), number));
    }

    public List<Object> published() {
        return List.copyOf(published);
    }
}
