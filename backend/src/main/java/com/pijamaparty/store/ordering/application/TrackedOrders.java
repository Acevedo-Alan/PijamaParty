package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-14 (T-E3-08): the order of a tracking link, read as the buyer that holds its token. Joins the caller's
 * transaction, or runs in its own.
 */
public interface TrackedOrders {

    /** Empty when no order has this token, or Row Level Security hides it from the actor. */
    Optional<TrackedOrder> find(TrackingToken token);

    /**
     * What the tracking page may show, and the id to pay it with: never the national ID, the email, the phone or the
     * street. {@link #toString()} hides the buyer's name and city (R-05).
     *
     * @param lines in the cart's order
     */
    record TrackedOrder(OrderId id, long number, OrderStatus status, Instant createdAt, String buyerName, String city,
                        Province province, List<OrderLine> lines, OrderAmounts amounts, Optional<String> couponCode,
                        Instant reservationExpiresAt, Optional<String> shipmentTrackingNumber) {

        public TrackedOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(buyerName, "buyerName");
            Objects.requireNonNull(city, "city");
            Objects.requireNonNull(province, "province");
            lines = List.copyOf(lines);
            Objects.requireNonNull(amounts, "amounts");
            Objects.requireNonNull(couponCode, "couponCode");
            Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
            Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
        }

        @Override
        public String toString() {
            return "TrackedOrder[id=" + id + ", number=" + number + ", status=" + status + ", buyer=***, city=***"
                + ", province=" + province + ", lines=" + lines.size() + ", amounts=" + amounts + "]";
        }
    }
}
