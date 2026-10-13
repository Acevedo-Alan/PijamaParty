package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import java.time.Instant;

/** Outbound write port (UC-18): corrects the tracking number of a shipped order, within the caller's transaction. */
public interface ShipmentTrackingNumbers {

    /** Replaces the number only while the order is {@code SHIPPED}, and answers whether it did. */
    boolean correct(OrderId order, ShipmentTrackingNumber number, Instant at);
}
