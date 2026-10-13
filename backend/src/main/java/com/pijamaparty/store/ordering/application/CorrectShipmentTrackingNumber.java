package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/** UC-18: the administrator corrects the tracking number of a shipped order; {@code DELIVERED} freezes it. */
public interface CorrectShipmentTrackingNumber {

    Result execute(Command command);

    /** @param shipmentTrackingNumber as typed */
    record Command(Actor actor, OrderId order, String shipmentTrackingNumber) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
        }
    }

    sealed interface Result {

        record Corrected(ShipmentTrackingNumber number) implements Result {

            public Corrected {
                Objects.requireNonNull(number, "number");
            }
        }

        record Forbidden() implements Result {}

        record NotFound() implements Result {}

        /** Only a {@code SHIPPED} order's number can be corrected. */
        record NotShipped(OrderStatus current) implements Result {

            public NotShipped {
                Objects.requireNonNull(current, "current");
            }
        }

        /** Blank, or longer than 60 characters. */
        record InvalidTrackingNumber() implements Result {}
    }
}
