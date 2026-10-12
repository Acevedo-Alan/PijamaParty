package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.testing.InMemoryStore;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of every coupon port, with the database's rules: unique codes, and a coupon with uses cannot be deleted. Uses
 * are {@link #redeem}ed, as confirmed orders will (E3).
 */
public class InMemoryCouponStore
    implements LoadCoupon, CouponUses, CouponSummaries, SaveCoupon, RemoveStoredCoupon {

    /** Arbitrary creation instant of {@link #save}d coupons. */
    private static final Instant ARRANGED = Instant.parse("2026-10-01T12:00:00Z");

    private final InMemoryStore<CouponId, Row> coupons = InMemoryStore.ofImmutable(row -> row.coupon().id());
    private final Map<CouponId, Long> uses = new ConcurrentHashMap<>();

    /** Test arrangement: stores the coupon, like {@link #add} without the result. */
    public void save(Coupon coupon) {
        if (add(coupon, ARRANGED) != SaveCoupon.Outcome.SAVED) {
            throw new IllegalArgumentException("code already used");
        }
    }

    /** {@code orders} more confirmed orders used the coupon (E3). */
    public void redeem(CouponId id, int orders) {
        uses.merge(id, (long) orders, Long::sum);
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return coupons.findAll(row -> row.coupon().code().equals(code)).stream().findFirst().map(Row::coupon);
    }

    @Override
    public Optional<Coupon> findById(CouponId id) {
        return coupons.findById(id).map(Row::coupon);
    }

    @Override
    public long count(CouponId id) {
        return uses.getOrDefault(id, 0L);
    }

    @Override
    public List<CouponView> listNewestFirst() {
        return coupons.findAll().stream()
            .sorted(Comparator.comparing(Row::createdAt).thenComparing(row -> row.coupon().id().value()).reversed())
            .map(row -> CouponView.of(row.coupon(), count(row.coupon().id())))
            .toList();
    }

    @Override
    public synchronized SaveCoupon.Outcome add(Coupon coupon, Instant at) {
        return write(new Row(coupon, at));
    }

    @Override
    public synchronized SaveCoupon.Outcome update(Coupon coupon, Instant at) {
        Optional<Row> stored = coupons.findById(coupon.id());
        if (stored.isEmpty()) {
            return SaveCoupon.Outcome.NOT_FOUND;
        }
        return write(new Row(coupon, stored.get().createdAt()));
    }

    private SaveCoupon.Outcome write(Row row) {
        boolean codeTaken = !coupons.findAll(other -> other.coupon().code().equals(row.coupon().code())
            && !other.coupon().id().equals(row.coupon().id())).isEmpty();
        if (codeTaken) {
            return SaveCoupon.Outcome.CODE_ALREADY_USED;
        }
        coupons.save(row);
        return SaveCoupon.Outcome.SAVED;
    }

    @Override
    public synchronized RemoveStoredCoupon.Outcome remove(CouponId id) {
        if (count(id) > 0) {
            return RemoveStoredCoupon.Outcome.HAS_USES;
        }
        return coupons.delete(id) ? RemoveStoredCoupon.Outcome.REMOVED : RemoveStoredCoupon.Outcome.NOT_FOUND;
    }

    private record Row(Coupon coupon, Instant createdAt) {}
}
