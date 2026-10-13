package com.pijamaparty.store.ordering.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * UC-18: a change of status the administrator asks for, decided before the order is touched. The administrator moves
 * an order to {@code PREPARING}, {@code SHIPPED} (with the tracking number), {@code DELIVERED} or {@code CANCELLED},
 * as the state diagram allows; {@code PAID} and {@code EXPIRED} only come from the payment and the expiry.
 *
 * @param trackingNumber present exactly when the order is shipped
 */
public record StatusChange(OrderStatus from, OrderStatus to, Optional<ShipmentTrackingNumber> trackingNumber,
                           StockEffect stock) {

    private static final Set<OrderStatus> SET_BY_THE_ADMINISTRATOR =
        EnumSet.of(OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    public StatusChange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(stock, "stock");
    }

    /** What the change does to the order's stock. */
    public enum StockEffect {
        NONE,
        /** An order awaiting payment gives its reservation back, as expiring does. */
        RELEASE,
        /** A cancelled paid order puts its deducted units back, because the administrator said so. */
        RESTOCK
    }

    /** The statuses the administrator may move an order in {@code status} to, in the order of {@link OrderStatus}. */
    public static Set<OrderStatus> nextFor(OrderStatus status) {
        Set<OrderStatus> next = EnumSet.noneOf(OrderStatus.class);
        SET_BY_THE_ADMINISTRATOR.stream().filter(status::canBecome).forEach(next::add);
        return Collections.unmodifiableSet(next);
    }

    /** The tracking number can be corrected while the order is shipped; delivering it freezes the number. */
    public static boolean allowsTrackingNumberCorrection(OrderStatus status) {
        return status == OrderStatus.SHIPPED;
    }

    /**
     * Decides the change from {@code from}, the order's current status, to {@code to}.
     *
     * @param restock the administrator's answer to "put the stock back?", only asked when cancelling a paid or
     *     preparing order
     */
    public static Decision decide(OrderStatus from, OrderStatus to, Optional<ShipmentTrackingNumber> trackingNumber,
                                  Optional<Boolean> restock) {
        if (!nextFor(from).contains(to)) {
            return new Decision.InvalidTransition();
        }
        if (to == OrderStatus.SHIPPED && trackingNumber.isEmpty()) {
            return new Decision.MissingTrackingNumber();
        }
        if (to != OrderStatus.SHIPPED && trackingNumber.isPresent()) {
            return new Decision.UnexpectedField("shipmentTrackingNumber");
        }
        boolean decidesStock = to == OrderStatus.CANCELLED
            && (from == OrderStatus.PAID || from == OrderStatus.PREPARING);
        if (decidesStock && restock.isEmpty()) {
            return new Decision.RestockDecisionRequired();
        }
        if (!decidesStock && restock.isPresent()) {
            return new Decision.UnexpectedField("restock");
        }
        StockEffect stock;
        if (to == OrderStatus.CANCELLED && from == OrderStatus.AWAITING_PAYMENT) {
            stock = StockEffect.RELEASE;
        } else if (decidesStock && restock.get()) {
            stock = StockEffect.RESTOCK;
        } else {
            stock = StockEffect.NONE;
        }
        return new Decision.Allowed(new StatusChange(from, to, trackingNumber, stock));
    }

    public sealed interface Decision {

        record Allowed(StatusChange change) implements Decision {

            public Allowed {
                Objects.requireNonNull(change, "change");
            }
        }

        /** Not in the state diagram, or a status the administrator does not set. */
        record InvalidTransition() implements Decision {}

        record MissingTrackingNumber() implements Decision {}

        /** Cancelling a paid or preparing order needs the answer to "put the stock back?". */
        record RestockDecisionRequired() implements Decision {}

        /** A field this change does not take. */
        record UnexpectedField(String field) implements Decision {

            public UnexpectedField {
                Objects.requireNonNull(field, "field");
            }
        }
    }
}
