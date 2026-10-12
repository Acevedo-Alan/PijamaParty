package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.Coupon;
import java.time.Instant;

/**
 * Outbound port: writes coupons within the caller's transaction (UC-11). The code is unique: a coupon whose code
 * another one already uses is not saved, and the caller's transaction stays usable.
 */
public interface SaveCoupon {

    /** Inserts a new coupon, created {@code at}. Never answers {@link Outcome#NOT_FOUND}. */
    Outcome add(Coupon coupon, Instant at);

    /** Updates a stored coupon, changed {@code at}; one deleted in the meantime is not brought back. */
    Outcome update(Coupon coupon, Instant at);

    enum Outcome {
        SAVED,
        CODE_ALREADY_USED,
        NOT_FOUND
    }
}
