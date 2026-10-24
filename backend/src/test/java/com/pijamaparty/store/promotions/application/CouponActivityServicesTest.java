package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-37 (T-E8-03): what the coupon use cases record in the activity log, and when they record nothing. */
class CouponActivityServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);

    private final InMemoryCouponStore coupons = new InMemoryCouponStore();
    private final InMemoryCouponActivity activity = new InMemoryCouponActivity();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CreateCoupon create = new CreateCouponService(coupons, activity, new InMemoryIdGenerator(), clock);
    private final UpdateCoupon update = new UpdateCouponService(coupons, coupons, coupons, activity, clock);
    private final ActivateCoupon activate = new ActivateCouponService(coupons, coupons, coupons, activity, clock);
    private final DeactivateCoupon deactivate =
        new DeactivateCouponService(coupons, coupons, coupons, activity, clock);
    private final DeleteCoupon delete = new DeleteCouponService(coupons, coupons, activity);

    @Test
    void givenACoupon_whenCreated_thenTheEntryNamesItByItsCode() {
        CouponId id = created(percentage("VERANO10", 10));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.COUPON_CREATED,
            ActivitySubject.of(SubjectType.COUPON, id.value(), "VERANO10")));
    }

    @Test
    void givenACoupon_whenItsTermsChange_thenOnlyTheChangedOnesAreRecordedAsDisplayText() {
        CouponId id = created(percentage("VERANO10", 10));
        activity.clear();

        update.execute(new UpdateCoupon.Command(ADMIN, id, new CouponInput("VERANO15", CouponType.FIXED_AMOUNT,
            500_000, Optional.of(LocalDate.of(2026, 12, 1)), Optional.empty(), Optional.of(100),
            Optional.of(2_000_050L))));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.COUPON_UPDATED,
            ActivitySubject.of(SubjectType.COUPON, id.value(), "VERANO15"), List.of(
                FieldChange.of("code", "VERANO10", "VERANO15"),
                FieldChange.of("type", "PERCENTAGE", "FIXED_AMOUNT"),
                FieldChange.of("value", "10%", "$5.000"),
                FieldChange.of("validFrom", null, "01/12/2026"),
                FieldChange.of("maxUses", null, "100"),
                FieldChange.of("minimumPurchase", null, "$20.000,50"))));
    }

    @Test
    void givenAnAutomaticCoupon_whenOneProductOfItsScopeIsSwappedForAnother_thenTheScopeIsRecordedAnyway() {
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, scoped(Set.of(UUID.randomUUID())), true)));
        activity.clear();

        update.execute(new UpdateCoupon.Command(ADMIN, id, scoped(Set.of(UUID.randomUUID()))));

        assertThat(activity.single().changes()).containsExactly(FieldChange.of("scopeProducts", "1", "1"));
    }

    @Test
    void givenACoupon_whenSavedWithTheSameTermsOrRefused_thenNothingIsRecorded() {
        CouponId id = created(percentage("VERANO10", 10));
        created(percentage("INVIERNO", 10));
        activity.clear();

        update.execute(new UpdateCoupon.Command(ADMIN, id, percentage("verano10", 10)));
        update.execute(new UpdateCoupon.Command(ADMIN, id, percentage("INVIERNO", 15)));
        update.execute(new UpdateCoupon.Command(ADMIN, id, percentage("VERANO10", 101)));

        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenACoupon_whenDeactivatedAndActivatedTwice_thenOnlyTheChangesAreRecorded() {
        CouponId id = created(percentage("VERANO10", 10));
        activity.clear();

        activate.execute(new ActivateCoupon.Command(ADMIN, id));
        deactivate.execute(new DeactivateCoupon.Command(ADMIN, id));
        deactivate.execute(new DeactivateCoupon.Command(ADMIN, id));
        activate.execute(new ActivateCoupon.Command(ADMIN, id));

        assertThat(activity.entries()).extracting(ActivityEntry::action)
            .containsExactly(ActivityAction.COUPON_DEACTIVATED, ActivityAction.COUPON_ACTIVATED);
    }

    @Test
    void givenAnUnusedAndAUsedCoupon_whenBothAreDeleted_thenOnlyTheDeletedOneIsRecordedWithItsCode() {
        CouponId unused = created(percentage("VERANO10", 10));
        CouponId used = created(percentage("INVIERNO", 10));
        coupons.redeem(used, 1);
        activity.clear();

        delete.execute(new DeleteCoupon.Command(ADMIN, unused));
        delete.execute(new DeleteCoupon.Command(ADMIN, used));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.COUPON_DELETED,
            ActivitySubject.of(SubjectType.COUPON, unused.value(), "VERANO10")));
    }

    private CouponId created(CouponInput input) {
        return idOf(create.execute(new CreateCoupon.Command(ADMIN, input)));
    }

    private static CouponInput percentage(String code, long percent) {
        return new CouponInput(code, CouponType.PERCENTAGE, percent, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());
    }

    private static CouponInput scoped(Set<UUID> products) {
        return new CouponInput("VERANO10", CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), new CouponInput.Scope(products, Set.of()));
    }

    private static CouponId idOf(CreateCoupon.Result result) {
        return ((CreateCoupon.Result.Created) result).coupon().id();
    }
}
