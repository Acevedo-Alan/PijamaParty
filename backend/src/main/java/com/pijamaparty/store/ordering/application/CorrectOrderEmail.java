package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Objects;

/**
 * UC-17 (T-E3-23): the buyer that holds an order's tracking link corrects the email of the order; on a paid order its
 * confirmation is sent again. Run as that buyer.
 */
public interface CorrectOrderEmail {

    Result execute(Command command);

    /** @param email as typed; personal data (R-05), so {@link #toString()} hides it */
    record Command(TrackingToken token, String email) {

        public Command {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(email, "email");
        }

        @Override
        public String toString() {
            return "Command[token=***, email=***]";
        }
    }

    sealed interface Result {

        /** @param confirmationResent whether the order is paid and its confirmation goes to the new email now */
        record Corrected(boolean confirmationResent) implements Result {}

        /** Not an email by the rule of UC-13. */
        record InvalidEmail() implements Result {}

        /** No order has this token. */
        record NotFound() implements Result {}

        /** Cancelled or expired. */
        record Closed(OrderStatus status) implements Result {

            public Closed {
                Objects.requireNonNull(status, "status");
            }
        }
    }
}
