package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderPeriod;
import com.pijamaparty.store.ordering.domain.OrderSearch;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListOrdersService implements ListOrders {

    private final OrderPages orderPages;
    private final Clock clock;

    ListOrdersService(OrderPages orderPages, Clock clock) {
        this.orderPages = orderPages;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<OrderSearch> search;
        switch (OrderSearch.parse(query.search().orElse(""))) {
            case OrderSearch.Parsed.Invalid invalid -> {
                return new Result.InvalidSearch();
            }
            case OrderSearch.Parsed.None none -> search = Optional.empty();
            case OrderSearch.Parsed.Valid valid -> search = Optional.of(valid.search());
        }
        Optional<OrderPeriod> period = OrderPeriod.of(query.from(), query.to());
        if (period.isEmpty()) {
            return new Result.InvalidPeriod();
        }
        // R-07: the dates are the store's, the zone of the injected clock.
        var filter = new OrderFilter(query.status(), search, period.get().start(clock.getZone()),
            period.get().end(clock.getZone()));
        // One more than a page tells whether another page follows.
        List<OrderSummary> rows = orderPages.page(filter, Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
