package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-18: the administrator moves an order from the status it saw to another one, as the state diagram allows, in one
 * transaction: {@code SHIPPED} saves the tracking number, {@code DELIVERED} the delivery instant (UC-29), cancelling
 * an order awaiting payment releases its reservation, and cancelling a paid or preparing one puts its stock back if
 * the administrator says so. No email is sent (UC-17).
 */
public interface ChangeOrderStatus {

    Result execute(Command command);

    /**
     * @param from the status the administrator saw: the change applies only if the order is still in it
     * @param shipmentTrackingNumber as typed; only for {@code SHIPPED}
     * @param restock only when cancelling a paid or preparing order
     */
    record Command(Actor actor, OrderId order, OrderStatus from, OrderStatus to,
                   Optional<String> shipmentTrackingNumber, Optional<Boolean> restock) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(shipmentTrackingNumber, "shipmentTrackingNumber");
            Objects.requireNonNull(restock, "restock");
        }
    }

    sealed interface Result {

        record Changed(OrderStatus status) implements Result {

            public Changed {
                Objects.requireNonNull(status, "status");
            }
        }

        record Forbidden() implements Result {}

        record NotFound() implements Result {}

        /** The order is no longer in the status the administrator saw: nothing changed. */
        record StatusChanged(OrderStatus current) implements Result {

            public StatusChanged {
                Objects.requireNonNull(current, "current");
            }
        }

        /** Not in the state diagram, or a status only the payment or the expiry sets. */
        record InvalidTransition() implements Result {}

        record MissingTrackingNumber() implements Result {}

        /** Blank, or longer than 60 characters. */
        record InvalidTrackingNumber() implements Result {}

        record RestockDecisionRequired() implements Result {}

        /** A field this change does not take: {@code shipmentTrackingNumber} or {@code restock}. */
        record UnexpectedField(String field) implements Result {

            public UnexpectedField {
                Objects.requireNonNull(field, "field");
            }
        }
    }
}
