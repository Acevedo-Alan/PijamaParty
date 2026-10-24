package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.promotions.domain.Coupon;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeactivateCouponService implements DeactivateCoupon {

    private final LoadCoupon loadCoupon;
    private final SaveCoupon saveCoupon;
    private final CouponUses uses;
    private final CouponActivity activity;
    private final Clock clock;

    DeactivateCouponService(LoadCoupon loadCoupon, SaveCoupon saveCoupon, CouponUses uses, CouponActivity activity,
                            Clock clock) {
        this.loadCoupon = loadCoupon;
        this.saveCoupon = saveCoupon;
        this.uses = uses;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Coupon> stored = loadCoupon.findById(command.couponId());
        if (stored.isEmpty()) {
            return new Result.NotFound();
        }
        Coupon coupon = stored.get().deactivated();
        return switch (saveCoupon.update(coupon, clock.instant())) {
            case SAVED -> {
                if (stored.get().active()) {
                    activity.record(CouponEntries.entry(command.actor(), ActivityAction.COUPON_DEACTIVATED, coupon));
                }
                yield new Result.Deactivated(CouponView.of(coupon, uses.count(coupon.id())));
            }
            case NOT_FOUND -> new Result.NotFound();
            case CODE_ALREADY_USED ->
                throw new IllegalStateException("deactivating does not change the code, so saving cannot collide");
        };
    }
}
