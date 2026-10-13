package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.net.URI;
import java.util.Objects;

/**
 * UC-14: opens the payment of an order awaiting payment at its provider, after the checkout committed (and again from
 * the tracking link, T-E3-08). Run as the buyer that holds the order's tracking token, outside any transaction.
 */
public interface OpenOrderPayment {

    Result execute(Command command);

    record Command(OrderId orderId) {

        public Command {
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    sealed interface Result {

        /** The provider's checkout, where the buyer pays. */
        record Opened(URI paymentUrl) implements Result {}

        /** The provider could not be reached: the order still awaits payment, and paying can be retried. */
        record ProviderUnavailable() implements Result {}

        /** No such order, not awaiting payment, or its reservation is over. */
        record NotPayable() implements Result {}
    }
}
