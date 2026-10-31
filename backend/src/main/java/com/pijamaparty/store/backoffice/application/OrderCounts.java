package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound read port (T-E8-05, UC-33): how much order work waits. UC-33 names it {@code OrderWorkload}; it grew from
 * the menu's badge.
 */
public interface OrderCounts {

    /** Orders {@code PAID}, waiting to be prepared. */
    long paidWaiting();

    /** Orders {@code PREPARING}. */
    long preparing();

    /** Withdrawal requests {@code RECEIVED} (UC-29). */
    long receivedWithdrawals();

    /** The newest orders of any status, newest first. */
    List<RecentOrder> newest(int limit);

    /** Of {@code orderIds}, those still open: {@code PAID}, {@code PREPARING} or {@code SHIPPED}. */
    Set<UUID> openAmong(Collection<UUID> orderIds);
}
