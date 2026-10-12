package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteCouponService implements DeleteCoupon {

    private final RemoveStoredCoupon removeCoupon;

    DeleteCouponService(RemoveStoredCoupon removeCoupon) {
        this.removeCoupon = removeCoupon;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return switch (removeCoupon.remove(command.couponId())) {
            case REMOVED -> new Result.Deleted();
            case NOT_FOUND -> new Result.NotFound();
            case HAS_USES -> new Result.HasUses();
        };
    }
}
