package com.pijamaparty.store.ordering.adapter.in.event;

import com.pijamaparty.store.ordering.application.ConfirmOrderPayment;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.payments.api.PaymentApproved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-15: an approved payment pays its order. A plain (synchronous) listener, so it runs in the payment's transaction,
 * as {@code SYSTEM}, and the order, its stock and its coupon commit or roll back with the payment. Logs carry the
 * order number only (R-05).
 */
@Component
class PaymentApprovedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovedListener.class);

    private final ConfirmOrderPayment confirmOrderPayment;

    PaymentApprovedListener(ConfirmOrderPayment confirmOrderPayment) {
        this.confirmOrderPayment = confirmOrderPayment;
    }

    @EventListener
    void on(PaymentApproved event) {
        var command = new ConfirmOrderPayment.Command(OrderId.of(event.orderId()));
        switch (confirmOrderPayment.execute(command)) {
            case ConfirmOrderPayment.Result.Paid paid -> log.info("Order {} is paid", paid.number());
            case ConfirmOrderPayment.Result.AlreadyPaid already -> {
            }
            case ConfirmOrderPayment.Result.PaidAfterClosing late -> log.warn(
                "Order {} was paid with {} while {}: it stays so; refund the payment from the provider's panel",
                late.number(), event.provider(), late.status());
        }
    }
}
