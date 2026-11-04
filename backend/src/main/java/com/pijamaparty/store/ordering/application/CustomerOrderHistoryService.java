package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-34 (T-E8-11): {@link CustomerOrders} for the back office, read as the caller (the administrator). */
@Service
class CustomerOrderHistoryService implements CustomerOrders {

    private final CustomerOrderPages pages;
    private final CustomerPurchaseFigures figures;

    CustomerOrderHistoryService(CustomerOrderPages pages, CustomerPurchaseFigures figures) {
        this.pages = pages;
        this.figures = figures;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderHistory historyOf(UUID customerId, int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        UserId customer = UserId.of(customerId);
        // One more than a page tells whether another page follows.
        List<CustomerOrderSummary> rows = pages.page(customer, Math.multiplyExact(page, PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<OrderRow> orders = (hasMore ? rows.subList(0, PAGE_SIZE) : rows).stream()
            .map(row -> new OrderRow(row.id().value(), row.number(), row.createdAt(),
                Status.valueOf(row.status().name()), row.total()))
            .toList();
        CustomerPurchaseFigures.Figures totals = figures.figuresOf(customer);
        return new OrderHistory(orders, hasMore, totals.orders(), totals.purchases(), totals.spent(),
            totals.lastPurchase());
    }
}
