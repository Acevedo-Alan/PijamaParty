package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExpireOverdueOrderService implements ExpireOverdueOrder {

    private final OverdueOrders overdueOrders;
    private final OrderTransitions orderTransitions;
    private final StockReleases stockReleases;
    private final Clock clock;

    ExpireOverdueOrderService(OverdueOrders overdueOrders, OrderTransitions orderTransitions,
                              StockReleases stockReleases, Clock clock) {
        this.overdueOrders = overdueOrders;
        this.orderTransitions = orderTransitions;
        this.stockReleases = stockReleases;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute() {
        Instant now = clock.instant();
        Optional<OrderId> due = overdueOrders.lockNextDue(now);
        if (due.isEmpty()) {
            return new Result.NoneDue();
        }
        OrderId order = due.get();
        // RNF-20.5: the order is locked and awaiting payment, so the conditional transition always applies.
        if (!orderTransitions.transition(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, now)) {
            throw new IllegalStateException("order " + order + " left AWAITING_PAYMENT while locked");
        }
        stockReleases.release(order);
        return new Result.Expired(order);
    }
}
