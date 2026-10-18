package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.promotions.domain.Coupon;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateCouponService implements UpdateCoupon {

    private final LoadCoupon loadCoupon;
    private final SaveCoupon saveCoupon;
    private final CouponUses uses;
    private final Clock clock;

    UpdateCouponService(LoadCoupon loadCoupon, SaveCoupon saveCoupon, CouponUses uses, Clock clock) {
        this.loadCoupon = loadCoupon;
        this.saveCoupon = saveCoupon;
        this.uses = uses;
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
        return switch (command.coupon().parse(command.couponId(), stored.get().active(), stored.get().automatic())) {
            case CouponInput.Parsed.Invalid invalid -> new Result.InvalidFields(invalid.fields());
            case CouponInput.Parsed.Valid valid -> save(valid.coupon());
        };
    }

    private Result save(Coupon coupon) {
        return switch (saveCoupon.update(coupon, clock.instant())) {
            case SAVED -> new Result.Updated(CouponView.of(coupon, uses.count(coupon.id())));
            case CODE_ALREADY_USED -> new Result.CodeAlreadyUsed(coupon.code().value());
            case NOT_FOUND -> new Result.NotFound();
        };
    }
}
