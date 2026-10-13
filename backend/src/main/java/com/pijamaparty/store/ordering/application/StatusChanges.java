package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.StatusChange;
import java.time.Instant;

/** Outbound write port (UC-18): applies the administrator's change of status, within the caller's transaction. */
public interface StatusChanges {

    /**
     * Applies the change only while the order is in {@code change.from()} (RNF-20.5): {@code SHIPPED} also saves the
     * tracking number, and {@code DELIVERED} the delivery instant, {@code at}. Answers whether it moved. The change's
     * stock effect is not this port's.
     *
     * @throws RuntimeException when the state diagram does not allow the transition
     */
    boolean change(OrderId order, StatusChange change, Instant at);
}
