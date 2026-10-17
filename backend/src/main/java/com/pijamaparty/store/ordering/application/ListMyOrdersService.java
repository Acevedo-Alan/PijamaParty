package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows a customer only its own orders (V14). */
@Service
class ListMyOrdersService implements ListMyOrders {

    private final CustomerOrderPages customerOrderPages;

    ListMyOrdersService(CustomerOrderPages customerOrderPages) {
        this.customerOrderPages = customerOrderPages;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.CUSTOMER) || query.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        // One more than a page tells whether another page follows.
        List<CustomerOrderSummary> rows = customerOrderPages.page(query.actor().userId().get(),
            Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
