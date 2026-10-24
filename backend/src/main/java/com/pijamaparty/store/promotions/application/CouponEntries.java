package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.ActivityValues;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.FieldChanges;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.domain.Coupon;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** UC-37: the activity entries of the coupon use cases, labelled with the coupon's code (T-E8-03). */
final class CouponEntries {

    private CouponEntries() {
    }

    static ActivityEntry entry(Actor administrator, ActivityAction action, Coupon coupon, List<FieldChange> changes) {
        return new ActivityEntry(administrator.userId().orElseThrow(
            () -> new IllegalArgumentException("only an administrator changes coupons")), action,
            ActivitySubject.of(SubjectType.COUPON, coupon.id().value(), coupon.code().value()), changes);
    }

    static ActivityEntry entry(Actor administrator, ActivityAction action, Coupon coupon) {
        return entry(administrator, action, coupon, List.of());
    }

    /** The terms that differ; the scope as how many products and categories, recorded whenever its set changed. */
    static FieldChanges changes(Coupon before, Coupon after) {
        return new FieldChanges()
            .add("code", before.code().value(), after.code().value())
            .add("type", before.type().name(), after.type().name())
            .add("value", value(before), value(after))
            .add("validFrom", date(before.validFrom()), date(after.validFrom()))
            .add("validUntil", date(before.validUntil()), date(after.validUntil()))
            .add("maxUses", before.maxUses().map(String::valueOf).orElse(null),
                after.maxUses().map(String::valueOf).orElse(null))
            .add("minimumPurchase", before.minimumPurchase().map(ActivityValues::pesos).orElse(null),
                after.minimumPurchase().map(ActivityValues::pesos).orElse(null))
            .addWhen(!before.scope().productIds().equals(after.scope().productIds()), "scopeProducts",
                String.valueOf(before.scope().productIds().size()), String.valueOf(after.scope().productIds().size()))
            .addWhen(!before.scope().categoryIds().equals(after.scope().categoryIds()), "scopeCategories",
                String.valueOf(before.scope().categoryIds().size()),
                String.valueOf(after.scope().categoryIds().size()));
    }

    /** {@code 15%}, or the amount as {@code $5.000}. */
    private static String value(Coupon coupon) {
        return coupon.type() == CouponType.PERCENTAGE
            ? coupon.discountValue() + "%"
            : ActivityValues.pesos(Money.of(coupon.discountValue()));
    }

    private static String date(Optional<LocalDate> date) {
        return date.map(ActivityValues::date).orElse(null);
    }
}
