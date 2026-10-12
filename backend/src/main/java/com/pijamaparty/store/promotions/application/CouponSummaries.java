package com.pijamaparty.store.promotions.application;

import java.util.List;

/** Outbound port, read side of the back-office list (UC-11). */
public interface CouponSummaries {

    /** Every coupon, active or not, newest first, each with its number of uses. */
    List<CouponView> listNewestFirst();
}
