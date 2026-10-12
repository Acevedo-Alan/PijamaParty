package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link LoadCoupon} and {@link CouponUses}: coupons are {@link #add}ed and their uses {@link #redeem}ed. */
public class InMemoryCouponStore implements LoadCoupon, CouponUses {

    private final InMemoryStore<CouponId, Coupon> coupons = InMemoryStore.ofImmutable(Coupon::id);
    private final Map<CouponId, Long> uses = new ConcurrentHashMap<>();

    public void add(Coupon coupon) {
        coupons.save(coupon);
    }

    /** {@code orders} more confirmed orders used the coupon (E3). */
    public void redeem(CouponId id, int orders) {
        uses.merge(id, (long) orders, Long::sum);
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return coupons.findAll(coupon -> coupon.code().equals(code)).stream().findFirst();
    }

    @Override
    public Optional<Coupon> findById(CouponId id) {
        return coupons.findById(id);
    }

    @Override
    public long count(CouponId id) {
        return uses.getOrDefault(id, 0L);
    }
}
