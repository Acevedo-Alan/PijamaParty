package com.pijamaparty.store.ordering.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/** The amounts of an order, calculated by the server (R-01, R-02): total = subtotal − discount + shipping. */
public record OrderAmounts(Money subtotal, Money discount, Money shipping, Money total) {

    public OrderAmounts {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(shipping, "shipping");
        Objects.requireNonNull(total, "total");
        if (subtotal.isZero()) {
            throw new IllegalArgumentException("an order sells something");
        }
        if (discount.isGreaterThan(subtotal)) {
            throw new IllegalArgumentException("the discount is at most the subtotal");
        }
        if (!total.equals(subtotal.minus(discount).plus(shipping))) {
            throw new IllegalArgumentException("the total is the subtotal less the discount plus shipping");
        }
    }

    public static OrderAmounts of(Money subtotal, Money discount, Money shipping) {
        return new OrderAmounts(subtotal, discount, shipping, subtotal.minus(discount).plus(shipping));
    }
}
