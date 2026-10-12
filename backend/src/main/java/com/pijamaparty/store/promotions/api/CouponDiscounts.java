package com.pijamaparty.store.promotions.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-10: whether a coupon applies to a cart's subtotal today (store date, R-07) and its discount, calculated on the
 * server. Read only: a use is counted when an order is confirmed (E3), not here. Joins the caller's transaction.
 */
public interface CouponDiscounts {

    /**
     * The coupon of the code a customer typed, ignoring surrounding spaces and case; empty when no coupon has it or the
     * text cannot be a code.
     */
    Optional<CouponDiscount> byCode(String code, Money subtotal);

    /** The coupon a cart holds; empty when it no longer exists. */
    Optional<CouponDiscount> byId(UUID couponId, Money subtotal);
}
