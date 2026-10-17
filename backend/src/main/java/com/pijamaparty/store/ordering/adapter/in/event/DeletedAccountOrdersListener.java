package com.pijamaparty.store.ordering.adapter.in.event;

import com.pijamaparty.store.accounts.api.CustomerAccountDeleted;
import com.pijamaparty.store.ordering.application.UnlinkCustomerOrders;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-28 (T-E4-16): a deleted customer's orders are unlinked from the account. A plain (synchronous) listener, so it
 * runs in the deletion's transaction, as that customer, and commits or rolls back with it. Idempotent: a second run
 * finds no order of the customer.
 */
@Component
class DeletedAccountOrdersListener {

    private final UnlinkCustomerOrders unlinkCustomerOrders;

    DeletedAccountOrdersListener(UnlinkCustomerOrders unlinkCustomerOrders) {
        this.unlinkCustomerOrders = unlinkCustomerOrders;
    }

    @EventListener
    void on(CustomerAccountDeleted event) {
        unlinkCustomerOrders.execute(new UnlinkCustomerOrders.Command(UserId.of(event.userId())));
    }
}
