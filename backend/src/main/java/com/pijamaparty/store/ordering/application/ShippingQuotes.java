package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.util.Optional;

/** Outbound port: the shipping cost the checkout charges, as the shipping module calculates it (UC-12). */
public interface ShippingQuotes {

    /**
     * What shipping to {@code province} costs for a subtotal after the discount, free shipping included; empty when
     * there is no shipping to that province. Runs in the caller's transaction.
     */
    Optional<Money> costTo(Province province, Money subtotalAfterDiscount);
}
