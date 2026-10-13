package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Objects;

/**
 * UC-15, UC-16: the provider approved the payment of an order. An order awaiting payment becomes {@code PAID}, its
 * reservation a deduction for good, and its coupon gets a use, all in the payment's transaction, which must exist.
 * Runs as {@code SYSTEM}. Repeating it changes nothing (RNF-20.5).
 *
 * @throws IllegalStateException when there is no such order: its payment exists, so the order must
 */
public interface ConfirmOrderPayment {

    Result execute(Command command);

    record Command(OrderId orderId) {

        public Command {
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    sealed interface Result {

        record Paid(long number) implements Result {}

        /** Already paid, or further on: nothing changed. */
        record AlreadyPaid(long number) implements Result {}

        /**
         * The order expired or was cancelled before the approval arrived: it stays so, and its stock is not touched
         * (decided in T-E3-07). The payment has to be refunded from the provider's panel.
         */
        record PaidAfterClosing(long number, OrderStatus status) implements Result {

            public PaidAfterClosing {
                Objects.requireNonNull(status, "status");
            }
        }
    }
}
