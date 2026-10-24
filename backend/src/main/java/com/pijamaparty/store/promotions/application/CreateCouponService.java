package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreateCouponService implements CreateCoupon {

    private final SaveCoupon saveCoupon;
    private final CouponActivity activity;
    private final IdGenerator ids;
    private final Clock clock;

    CreateCouponService(SaveCoupon saveCoupon, CouponActivity activity, IdGenerator ids, Clock clock) {
        this.saveCoupon = saveCoupon;
        this.activity = activity;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return switch (command.coupon().parse(CouponId.of(ids.newId()), true, command.automatic())) {
            case CouponInput.Parsed.Invalid invalid -> new Result.InvalidFields(invalid.fields());
            case CouponInput.Parsed.Valid valid -> save(command.actor(), valid.coupon());
        };
    }

    private Result save(Actor administrator, Coupon coupon) {
        return switch (saveCoupon.add(coupon, clock.instant())) {
            case SAVED -> {
                activity.record(CouponEntries.entry(administrator, ActivityAction.COUPON_CREATED, coupon));
                yield new Result.Created(CouponView.of(coupon, 0));
            }
            case CODE_ALREADY_USED -> new Result.CodeAlreadyUsed(coupon.code().value());
            case NOT_FOUND -> throw new IllegalStateException("adding a coupon never misses a stored one");
        };
    }
}
