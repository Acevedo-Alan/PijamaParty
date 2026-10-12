package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link Coupons}: percentage coupons with an optional minimum purchase, which apply unless they are
 * {@link #reject}ed for another reason. Codes are matched as promotions does: ignoring surrounding spaces and case.
 */
public class InMemoryCoupons implements Coupons {

    private final Map<CouponId, Definition> coupons = new ConcurrentHashMap<>();

    /** A coupon of {@code percent} percent, rounded down, for a subtotal of at least {@code minimum}. */
    public CouponId percentage(String code, int percent, Money minimum) {
        CouponId id = CouponId.of(UUID.randomUUID());
        coupons.put(id, new Definition(code.toUpperCase(Locale.ROOT), percent, minimum, Optional.empty()));
        return id;
    }

    public CouponId percentage(String code, int percent) {
        return percentage(code, percent, Money.zero());
    }

    /** From now on the coupon does not apply, for {@code reason}: neither applicable nor below the minimum. */
    public void reject(CouponId id, CouponCheck.Status reason) {
        if (reason == CouponCheck.Status.APPLICABLE || reason == CouponCheck.Status.BELOW_MINIMUM) {
            throw new IllegalArgumentException("not a fixed reason: " + reason);
        }
        coupons.computeIfPresent(id, (key, coupon) -> coupon.rejectedFor(reason));
    }

    /** The administrator deleted it (UC-11). */
    public void delete(CouponId id) {
        coupons.remove(id);
    }

    @Override
    public Optional<CouponCheck> byCode(String code, Money subtotal) {
        String typed = code.strip().toUpperCase(Locale.ROOT);
        return coupons.entrySet().stream()
            .filter(entry -> entry.getValue().code().equals(typed))
            .findFirst()
            .map(entry -> entry.getValue().check(entry.getKey(), subtotal));
    }

    @Override
    public Optional<CouponCheck> byId(CouponId id, Money subtotal) {
        return Optional.ofNullable(coupons.get(id)).map(coupon -> coupon.check(id, subtotal));
    }

    private record Definition(String code, int percent, Money minimum, Optional<CouponCheck.Status> rejection) {

        Definition rejectedFor(CouponCheck.Status reason) {
            return new Definition(code, percent, minimum, Optional.of(reason));
        }

        CouponCheck check(CouponId id, Money subtotal) {
            if (rejection.isPresent()) {
                return new CouponCheck(id, code, rejection.get(), Money.zero(), Money.zero());
            }
            if (subtotal.isLessThan(minimum)) {
                return new CouponCheck(id, code, CouponCheck.Status.BELOW_MINIMUM, Money.zero(),
                    minimum.minus(subtotal));
            }
            return new CouponCheck(id, code, CouponCheck.Status.APPLICABLE, subtotal.percent(percent), Money.zero());
        }
    }
}
