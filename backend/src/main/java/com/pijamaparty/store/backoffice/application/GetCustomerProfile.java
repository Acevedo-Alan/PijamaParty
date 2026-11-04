package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-34 (T-E8-11): a customer's profile in the back office: its account (contact, providers, addresses) from
 * {@code accounts}, and one page of its orders with its purchase figures from {@code ordering}. Opening it (page 0)
 * records {@code CUSTOMER_VIEWED}; loading more of its orders records nothing.
 */
public interface GetCustomerProfile {

    Result execute(Query query);

    /** {@code ordersPage} counts from 0. */
    record Query(Actor actor, UUID customerId, int ordersPage) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(customerId, "customerId");
            if (ordersPage < 0) {
                throw new IllegalArgumentException("ordersPage must not be negative");
            }
        }
    }

    sealed interface Result {

        /** Personal data (R-05): {@link #toString()} prints only the id. */
        record Shown(CustomerProfile profile, OrderHistory orders) implements Result {

            public Shown {
                Objects.requireNonNull(profile, "profile");
                Objects.requireNonNull(orders, "orders");
            }

            @Override
            public String toString() {
                return "Shown[id=" + profile.id() + ", ***]";
            }
        }

        /** No customer with that id: unknown, deleted meanwhile (UC-28), or the administrator itself. */
        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
