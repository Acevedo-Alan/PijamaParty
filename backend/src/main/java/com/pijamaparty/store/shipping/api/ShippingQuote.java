package com.pijamaparty.store.shipping.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/** UC-12: what shipping to a province costs for a subtotal, or that the shop does not ship there. */
public sealed interface ShippingQuote {

    /** {@code amount} is zero for a free shipping: a cost of 0 to the province, or the free-shipping threshold. */
    record Cost(Money amount) implements ShippingQuote {

        public Cost {
            Objects.requireNonNull(amount, "amount");
        }
    }

    /** The province has no shipping cost. */
    record NoShipping() implements ShippingQuote {}
}
