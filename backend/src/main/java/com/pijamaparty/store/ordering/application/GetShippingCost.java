package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.util.Objects;

/**
 * UC-12, UC-13: step 1 of the checkout. The shipping cost of the actor's cart to a province and the amounts of the
 * summary, all calculated on the server. Read only.
 */
public interface GetShippingCost {

    Result execute(Query query);

    record Query(Actor actor, Province province) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(province, "province");
        }
    }

    sealed interface Result {

        /** {@code total} = {@code subtotal} − {@code discount} + {@code shipping}. */
        record Quoted(Province province, Money subtotal, Money discount, Money shipping, Money total)
            implements Result {

            public Quoted {
                Objects.requireNonNull(province, "province");
                Objects.requireNonNull(subtotal, "subtotal");
                Objects.requireNonNull(discount, "discount");
                Objects.requireNonNull(shipping, "shipping");
                if (!total.equals(subtotal.minus(discount).plus(shipping))) {
                    throw new IllegalArgumentException("the total is the subtotal less the discount plus shipping");
                }
            }
        }

        /** There is no shipping to the province. */
        record NoShipping(Province province) implements Result {}

        /** No cart, none of its lines available, or an actor that cannot own a cart. */
        record EmptyCart() implements Result {}
    }
}
