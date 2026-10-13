package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.net.URI;
import java.util.Objects;

/**
 * UC-14 (T-E3-08): pays again, from the tracking link, an order whose payment was not completed. Run as the buyer that
 * holds the token, outside any transaction.
 */
public interface RetryOrderPayment {

    Result execute(Command command);

    record Command(TrackingToken token) {

        public Command {
            Objects.requireNonNull(token, "token");
        }
    }

    sealed interface Result {

        /** The provider's checkout, where the buyer pays. */
        record Opened(URI paymentUrl) implements Result {}

        /** The provider could not be reached: the order still awaits payment, and paying can be retried. */
        record ProviderUnavailable() implements Result {}

        /** No longer awaiting payment, or its reservation is over. */
        record NotPayable() implements Result {}

        /** No order has this token. */
        record NotFound() implements Result {}
    }
}
