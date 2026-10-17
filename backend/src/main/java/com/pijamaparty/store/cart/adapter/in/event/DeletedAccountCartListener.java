package com.pijamaparty.store.cart.adapter.in.event;

import com.pijamaparty.store.accounts.api.CustomerAccountDeleted;
import com.pijamaparty.store.cart.application.DeleteCustomerCart;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-28 (T-E4-16): a deleted customer's cart goes with its account. A plain (synchronous) listener, so it runs in the
 * deletion's transaction, as that customer, and commits or rolls back with it. Idempotent: a second run finds no cart.
 */
@Component
class DeletedAccountCartListener {

    private final DeleteCustomerCart deleteCustomerCart;

    DeletedAccountCartListener(DeleteCustomerCart deleteCustomerCart) {
        this.deleteCustomerCart = deleteCustomerCart;
    }

    @EventListener
    void on(CustomerAccountDeleted event) {
        deleteCustomerCart.execute(new DeleteCustomerCart.Command(UserId.of(event.userId())));
    }
}
