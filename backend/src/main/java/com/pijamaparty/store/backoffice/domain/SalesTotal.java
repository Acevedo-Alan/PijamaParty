package com.pijamaparty.store.backoffice.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/** UC-33: the amount and the number of orders that count as sales in a period. */
public record SalesTotal(Money amount, long orders) {

    public static final SalesTotal NONE = new SalesTotal(Money.zero(), 0);

    public SalesTotal {
        Objects.requireNonNull(amount, "amount");
        if (orders < 0) {
            throw new IllegalArgumentException("orders must not be negative");
        }
    }

    /** The amount divided by the orders, rounded down to the cent; empty ("—") without orders. */
    public Optional<Money> averageTicket() {
        return orders == 0 ? Optional.empty() : Optional.of(Money.of(amount.cents() / orders));
    }
}
