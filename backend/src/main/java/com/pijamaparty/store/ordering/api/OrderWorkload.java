package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * T-E8-05 and UC-33: how much order work waits, for the back-office menu and summary. Read in its own transaction, or
 * the caller's, as the caller: the administrator, whom Row Level Security lets read every order.
 */
public interface OrderWorkload {

    /** Orders {@code PAID}, waiting to be prepared. */
    long paidWaiting();

    /** Orders {@code PREPARING}. */
    long preparing();

    /** Withdrawal requests {@code RECEIVED}, not yet resolved (UC-29). */
    long receivedWithdrawals();

    /** The newest orders of any status, newest first. */
    List<RecentOrder> newest(int limit);

    /** Of {@code orderIds}, those still open: {@code PAID}, {@code PREPARING} or {@code SHIPPED}. */
    Set<UUID> openAmong(Collection<UUID> orderIds);

    /** Personal data (R-05): {@link #toString()} prints only the number. */
    record RecentOrder(UUID id, long number, Instant createdAt, String buyerName, Status status, Money total) {

        public RecentOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(buyerName, "buyerName");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(total, "total");
        }

        @Override
        public String toString() {
            return "RecentOrder[number=" + number + ", ***]";
        }
    }
}
