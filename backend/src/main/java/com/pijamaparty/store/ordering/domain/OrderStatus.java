package com.pijamaparty.store.ordering.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The seven states of an order (GLOSSARY.md) and the transitions of the UC-18 state diagram:
 * {@code AWAITING_PAYMENT → PAID → PREPARING → SHIPPED → DELIVERED}; {@code AWAITING_PAYMENT → EXPIRED} or
 * {@code CANCELLED}; {@code PAID} or {@code PREPARING → CANCELLED}.
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    EXPIRED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        AWAITING_PAYMENT, EnumSet.of(PAID, EXPIRED, CANCELLED),
        PAID, EnumSet.of(PREPARING, CANCELLED),
        PREPARING, EnumSet.of(SHIPPED, CANCELLED),
        SHIPPED, EnumSet.of(DELIVERED),
        DELIVERED, EnumSet.noneOf(OrderStatus.class),
        CANCELLED, EnumSet.noneOf(OrderStatus.class),
        EXPIRED, EnumSet.noneOf(OrderStatus.class));

    /**
     * UC-24: whether the buyer of an order in this status bought its items: paid, and neither cancelled nor expired.
     * Who may review a product depends on it.
     */
    public boolean isBought() {
        return this == PAID || this == PREPARING || this == SHIPPED || this == DELIVERED;
    }

    /** Whether the state diagram allows going from this status to {@code next}. */
    public boolean canBecome(OrderStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }
}
