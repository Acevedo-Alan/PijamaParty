package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.util.Objects;

/**
 * UC-29 (T-E3-25): a person withdraws from a purchase with its order number and email, without an account. Run as
 * {@code SYSTEM}: only the system reads an order by its number and records requests (RNF-27).
 */
public interface RequestWithdrawal {

    Result execute(Command command);

    /**
     * @param orderNumber as typed, {@code 1001} or {@code #1001}
     * @param email       as typed; personal data (R-05), so {@link #toString()} hides it
     */
    record Command(String orderNumber, String email) {

        public Command {
            Objects.requireNonNull(orderNumber, "orderNumber");
            Objects.requireNonNull(email, "email");
        }

        @Override
        public String toString() {
            return "Command[orderNumber=" + orderNumber + ", email=***]";
        }
    }

    sealed interface Result {

        /** Recorded; its code is shown and emailed. */
        record Requested(WithdrawalCode code) implements Result {

            public Requested {
                Objects.requireNonNull(code, "code");
            }
        }

        /** The order already has an active request: its code, and nothing new is recorded or sent. */
        record AlreadyRequested(WithdrawalCode code) implements Result {

            public AlreadyRequested {
                Objects.requireNonNull(code, "code");
            }
        }

        /** No order has this number and email, or they are not usable; the same for all, so nothing is revealed. */
        record NoMatchingOrder() implements Result {}

        /** Awaiting payment, expired or cancelled: there is no payment to return. */
        record NotWithdrawable(OrderStatus status) implements Result {

            public NotWithdrawable {
                Objects.requireNonNull(status, "status");
            }
        }
    }
}
