package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.CountedCoupon;
import java.util.List;

/** Outbound port: the coupons that apply on their own (UC-23), read within the caller's transaction. */
public interface LoadAutomaticCoupons {

    /** The active automatic coupons with their uses, oldest first ({@code created_at}, then {@code id}). */
    List<CountedCoupon> activeOldestFirst();
}
