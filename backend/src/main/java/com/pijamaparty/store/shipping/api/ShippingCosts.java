package com.pijamaparty.store.shipping.api;

import com.pijamaparty.store.platform.domain.Money;

/**
 * UC-12, UC-13: the shipping cost the checkout charges, calculated on the server. Read only; joins the caller's
 * transaction.
 */
public interface ShippingCosts {

    /**
     * Shipping to {@code province} for a cart whose subtotal after the discount is {@code subtotalAfterDiscount}: free
     * once it reaches the free-shipping threshold; a province without shipping has none at any subtotal.
     */
    ShippingQuote quote(Province province, Money subtotalAfterDiscount);
}
