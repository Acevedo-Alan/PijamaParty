package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-22 (T-E4-08): at a customer's sign-in, the orders placed as a guest with its verified email join its account. An
 * order unlinked by an account deletion (UC-28) never joins again.
 */
public interface LinkGuestOrders {

    Result execute(Command command);

    /** {@code customer} is the customer signing in, whose credentials were already checked. */
    record Command(UserId customer) {

        public Command {
            Objects.requireNonNull(customer, "customer");
        }
    }

    sealed interface Result {

        /** {@code count} guest orders joined the account; 0 when there were none. */
        record Linked(int count) implements Result {}

        /** The account's email is not verified, or the user is not a customer: nothing is linked. */
        record NotVerified() implements Result {}
    }
}
