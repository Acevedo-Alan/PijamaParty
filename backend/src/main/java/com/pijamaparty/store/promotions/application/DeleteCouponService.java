package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.promotions.domain.Coupon;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteCouponService implements DeleteCoupon {

    private final LoadCoupon loadCoupon;
    private final RemoveStoredCoupon removeCoupon;
    private final CouponActivity activity;

    DeleteCouponService(LoadCoupon loadCoupon, RemoveStoredCoupon removeCoupon, CouponActivity activity) {
        this.loadCoupon = loadCoupon;
        this.removeCoupon = removeCoupon;
        this.activity = activity;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        // Read first: the entry names the coupon by its code, which is gone once it is deleted.
        Optional<Coupon> stored = loadCoupon.findById(command.couponId());
        return switch (removeCoupon.remove(command.couponId())) {
            case REMOVED -> {
                stored.ifPresent(coupon -> activity.record(
                    CouponEntries.entry(command.actor(), ActivityAction.COUPON_DELETED, coupon)));
                yield new Result.Deleted();
            }
            case NOT_FOUND -> new Result.NotFound();
            case HAS_USES -> new Result.HasUses();
        };
    }
}
