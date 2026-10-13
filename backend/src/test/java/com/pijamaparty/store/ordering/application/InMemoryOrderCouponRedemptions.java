package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Fake of {@link OrderCouponRedemptions}: the coupons that exist and the orders that used each, once. */
public class InMemoryOrderCouponRedemptions implements OrderCouponRedemptions {

    private record Redemption(UUID couponId, OrderId order) {}

    private final Set<UUID> coupons = new HashSet<>();
    private final Set<Redemption> redemptions = new HashSet<>();

    /** Test arrangement: a coupon that exists. */
    public synchronized void coupon(UUID couponId) {
        coupons.add(couponId);
    }

    /** Test inspection: how many orders used the coupon. */
    public synchronized long usesOf(UUID couponId) {
        return redemptions.stream().filter(redemption -> redemption.couponId().equals(couponId)).count();
    }

    @Override
    public synchronized void redeem(UUID couponId, OrderId order, Instant at) {
        if (coupons.contains(couponId)) {
            redemptions.add(new Redemption(couponId, order));
        }
    }
}
