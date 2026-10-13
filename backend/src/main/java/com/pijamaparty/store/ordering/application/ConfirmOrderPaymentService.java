package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.ConfirmableOrders.ConfirmableOrder;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConfirmOrderPaymentService implements ConfirmOrderPayment {

    private final ConfirmableOrders confirmableOrders;
    private final OrderTransitions orderTransitions;
    private final StockDeductions stockDeductions;
    private final OrderCouponRedemptions couponRedemptions;
    private final OrderEvents orderEvents;
    private final Clock clock;

    ConfirmOrderPaymentService(ConfirmableOrders confirmableOrders, OrderTransitions orderTransitions,
                               StockDeductions stockDeductions, OrderCouponRedemptions couponRedemptions,
                               OrderEvents orderEvents, Clock clock) {
        this.confirmableOrders = confirmableOrders;
        this.orderTransitions = orderTransitions;
        this.stockDeductions = stockDeductions;
        this.couponRedemptions = couponRedemptions;
        this.orderEvents = orderEvents;
        this.clock = clock;
    }

    /** Joins the payment's transaction, so the order commits or rolls back with it. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        ConfirmableOrder order = confirmableOrders.lock(command.orderId())
            .orElseThrow(() -> new IllegalStateException("order " + command.orderId() + " of a payment not found"));
        return switch (order.status()) {
            case AWAITING_PAYMENT -> pay(order);
            case PAID, PREPARING, SHIPPED, DELIVERED -> new Result.AlreadyPaid(order.number());
            case EXPIRED, CANCELLED -> new Result.PaidAfterClosing(order.number(), order.status());
        };
    }

    private Result pay(ConfirmableOrder order) {
        Instant now = clock.instant();
        // RNF-20.5: the order is locked and awaiting payment, so the conditional transition always applies.
        if (!orderTransitions.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, now)) {
            throw new IllegalStateException("order " + order.id() + " left AWAITING_PAYMENT while locked");
        }
        stockDeductions.deduct(order.id());
        order.couponId().ifPresent(coupon -> couponRedemptions.redeem(coupon, order.id(), now));
        // UC-17: the payment-confirmed emails are enqueued in this same transaction (RNF-23).
        orderEvents.orderPaid(order.id(), order.number());
        return new Result.Paid(order.number());
    }
}
