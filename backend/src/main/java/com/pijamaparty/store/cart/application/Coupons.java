package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;

/** UC-10: whether a coupon applies to a cart's subtotal and its discount, as promotions decides. Read only. */
public interface Coupons {

    /** The coupon of the code a customer typed, ignoring surrounding spaces and case; empty when there is none. */
    Optional<CouponCheck> byCode(String code, Money subtotal);

    /** The coupon a cart holds; empty when it no longer exists. */
    Optional<CouponCheck> byId(CouponId id, Money subtotal);
}
