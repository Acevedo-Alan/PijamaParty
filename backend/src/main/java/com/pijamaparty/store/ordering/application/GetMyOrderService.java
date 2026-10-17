package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;

/** UC-22. Read as the customer itself, in a short transaction of the port's own; V14 hides other orders. */
@Service
class GetMyOrderService implements GetMyOrder {

    private final CustomerOrderViews customerOrderViews;

    GetMyOrderService(CustomerOrderViews customerOrderViews) {
        this.customerOrderViews = customerOrderViews;
    }

    @Override
    public Result execute(Query query) {
        if (!query.actor().is(Role.CUSTOMER) || query.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        return customerOrderViews.find(query.actor().userId().get(), query.order())
            .<Result>map(Result.Found::new)
            .orElseGet(Result.NotFound::new);
    }
}
