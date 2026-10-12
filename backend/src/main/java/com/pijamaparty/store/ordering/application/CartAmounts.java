package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/** Outbound port: the amounts of the cart the checkout sells, as the cart module prices it (UC-09, UC-13). */
public interface CartAmounts {

    /**
     * The actor's cart; empty when it has none, none of its lines is available, or the actor cannot own a cart.
     * Runs in the caller's transaction, as {@code actor}.
     */
    Optional<Amounts> of(Actor actor);

    /** Only the available lines count in {@code subtotal}; {@code discount} is at most the subtotal. */
    record Amounts(Money subtotal, Money discount) {

        public Amounts {
            Objects.requireNonNull(subtotal, "subtotal");
            Objects.requireNonNull(discount, "discount");
            if (discount.isGreaterThan(subtotal)) {
                throw new IllegalArgumentException("the discount is at most the subtotal");
            }
        }

        public Money afterDiscount() {
            return subtotal.minus(discount);
        }
    }
}
