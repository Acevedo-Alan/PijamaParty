package com.pijamaparty.store.ordering.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins the account deletion's transaction, as the customer itself: the database lets a customer unlink its own orders
 * only once its user is gone (V36), so they commit or roll back with the deletion.
 */
@Service
class UnlinkCustomerOrdersService implements UnlinkCustomerOrders {

    private final CustomerOrderUnlinks unlinks;
    private final Clock clock;

    UnlinkCustomerOrdersService(CustomerOrderUnlinks unlinks, Clock clock) {
        this.unlinks = unlinks;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        return new Result.Unlinked(unlinks.unlinkAll(command.customer(), clock.instant()));
    }
}
