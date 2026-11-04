package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-34 (T-E8-11): a customer's orders and purchase figures, for the back office's customer profile. Only the orders
 * linked to the account count, never a guest's with the same email (UC-21, UC-28). Read as the caller (the
 * administrator, whom Row Level Security lets read every order), in its transaction.
 */
public interface CustomerOrders {

    int PAGE_SIZE = 20;

    /** One page of {@code customerId}'s orders, newest first, from page 0, with the figures of all of them. */
    OrderHistory historyOf(UUID customerId, int page);

    /**
     * @param orders the page, newest first
     * @param orderCount every linked order, in any status
     * @param purchases the orders that count as sales: paid and neither cancelled nor expired (UC-33)
     * @param spent the sum of the purchases' totals
     * @param lastPurchase when the newest purchase was placed
     */
    record OrderHistory(List<OrderRow> orders, boolean hasMore, long orderCount, long purchases, Money spent,
                        Optional<Instant> lastPurchase) {

        public OrderHistory {
            orders = List.copyOf(orders);
            Objects.requireNonNull(spent, "spent");
            Objects.requireNonNull(lastPurchase, "lastPurchase");
        }
    }

    /** An order as a row of the profile; nothing personal. */
    record OrderRow(UUID orderId, long number, Instant createdAt, Status status, Money total) {

        public OrderRow {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(total, "total");
        }
    }
}
