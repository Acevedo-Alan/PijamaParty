package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Outbound read port (UC-33): the order work beyond the counts by status, within the caller's transaction. */
public interface WorkloadStatistics {

    long receivedWithdrawals();

    /** Newest first (then the highest number). */
    List<RecentOrder> newest(int limit);

    /** Of {@code orderIds}, those {@code PAID}, {@code PREPARING} or {@code SHIPPED}. */
    Set<UUID> openAmong(Collection<UUID> orderIds);
}
