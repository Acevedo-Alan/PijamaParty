package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): a deleted customer's orders stay, for the shop's accounting and tax duties, with their copy of the
 * buyer's data, but without the account. An unlinked order never joins an account again (T-E4-08), so a new account
 * with the same email does not see it. Idempotent: a second run unlinks nothing.
 */
public interface UnlinkCustomerOrders {

    Result execute(Command command);

    record Command(UserId customer) {

        public Command {
            Objects.requireNonNull(customer, "customer");
        }
    }

    sealed interface Result {

        /** {@code count} orders were unlinked; 0 when there were none. */
        record Unlinked(int count) implements Result {}
    }
}
