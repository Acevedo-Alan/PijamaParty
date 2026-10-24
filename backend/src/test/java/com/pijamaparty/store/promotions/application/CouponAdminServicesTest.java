package com.pijamaparty.store.promotions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.promotions.domain.CouponCode;
import com.pijamaparty.store.promotions.domain.CouponId;
import com.pijamaparty.store.promotions.domain.CouponScope;
import com.pijamaparty.store.promotions.domain.CouponType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** UC-11: the back-office use cases of coupons, over the in-memory store. */
class CouponAdminServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final Actor CUSTOMER = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(),
        Optional.empty());

    private final InMemoryCouponStore coupons = new InMemoryCouponStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ListCouponsService list = new ListCouponsService(coupons);
    private final InMemoryCouponActivity activity = new InMemoryCouponActivity();
    private final CreateCouponService create =
        new CreateCouponService(coupons, activity, new InMemoryIdGenerator(), clock);
    private final UpdateCouponService update = new UpdateCouponService(coupons, coupons, coupons, activity, clock);
    private final ActivateCouponService activate =
        new ActivateCouponService(coupons, coupons, coupons, activity, clock);
    private final DeactivateCouponService deactivate =
        new DeactivateCouponService(coupons, coupons, coupons, activity, clock);
    private final DeleteCouponService delete = new DeleteCouponService(coupons, coupons, activity);

    @Test
    void givenTypedData_whenTheAdministratorCreatesACoupon_thenItIsActiveWithItsCodeInUpperCase() {
        CreateCoupon.Result result = create.execute(new CreateCoupon.Command(ADMIN, new CouponInput(" verano-10 ",
            CouponType.FIXED_AMOUNT, 500_000, Optional.of(LocalDate.of(2026, 12, 1)),
            Optional.of(LocalDate.of(2027, 2, 28)), Optional.of(100), Optional.of(2_000_000L))));

        CouponView created = new CouponView(CouponId.of(InMemoryIdGenerator.nth(1)), "VERANO-10",
            CouponType.FIXED_AMOUNT, 500_000, Optional.of(LocalDate.of(2026, 12, 1)),
            Optional.of(LocalDate.of(2027, 2, 28)), Optional.of(100), Optional.of(Money.of(2_000_000)), false,
            CouponScope.WHOLE_CART, true, 0);
        assertThat(result).isEqualTo(new CreateCoupon.Result.Created(created));
        assertThat(list.execute(new ListCoupons.Query(ADMIN))).isEqualTo(new ListCoupons.Result.Listed(
            List.of(created)));
    }

    @Test
    void givenAnExistingCode_whenAnotherWithTheSameCodeIsCreated_thenItIsRejected() {
        create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10")));

        assertThat(create.execute(new CreateCoupon.Command(ADMIN, percentage("verano10"))))
            .isEqualTo(new CreateCoupon.Result.CodeAlreadyUsed("VERANO10"));
        assertThat(coupons.listNewestFirst()).hasSize(1);
    }

    @Test
    void givenDataAgainstTheRules_whenCreating_thenEveryInvalidFieldIsNamedAndNothingIsSaved() {
        CouponInput invalid = new CouponInput("AB", CouponType.PERCENTAGE, 101, Optional.of(LocalDate.of(2026, 12, 2)),
            Optional.of(LocalDate.of(2026, 12, 1)), Optional.of(0), Optional.of(-1L));

        assertThat(create.execute(new CreateCoupon.Command(ADMIN, invalid))).isEqualTo(
            new CreateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.CODE,
                CouponInput.Field.DISCOUNT_VALUE, CouponInput.Field.VALIDITY, CouponInput.Field.MAX_USES,
                CouponInput.Field.MINIMUM_PURCHASE)));
        assertThat(create.execute(new CreateCoupon.Command(ADMIN, new CouponInput(null, CouponType.FIXED_AMOUNT,
            1_000_000_001L, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1_000_000_001L)))))
            .isEqualTo(new CreateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.CODE,
                CouponInput.Field.DISCOUNT_VALUE, CouponInput.Field.MINIMUM_PURCHASE)));
        assertThat(coupons.listNewestFirst()).isEmpty();
    }

    @Test
    void givenAUsedInactiveCoupon_whenEveryFieldIsEdited_thenItKeepsItsStateAndItsUses() {
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10"))));
        deactivate.execute(new DeactivateCoupon.Command(ADMIN, id));
        coupons.redeem(id, 4);

        UpdateCoupon.Result result = update.execute(new UpdateCoupon.Command(ADMIN, id, new CouponInput("invierno",
            CouponType.FIXED_AMOUNT, 300_000, Optional.empty(), Optional.of(LocalDate.of(2027, 8, 31)),
            Optional.of(10), Optional.empty())));

        assertThat(result).isEqualTo(new UpdateCoupon.Result.Updated(new CouponView(id, "INVIERNO",
            CouponType.FIXED_AMOUNT, 300_000, Optional.empty(), Optional.of(LocalDate.of(2027, 8, 31)),
            Optional.of(10), Optional.empty(), false, CouponScope.WHOLE_CART, false, 4)));
        assertThat(coupons.findByCode(new CouponCode("INVIERNO")))
            .isPresent();
    }

    @Test
    void givenAnotherCouponsCodeOrInvalidData_whenEditing_thenTheCouponStaysAsItWas() {
        create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10")));
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("INVIERNO"))));

        assertThat(update.execute(new UpdateCoupon.Command(ADMIN, id, percentage(" Verano10"))))
            .isEqualTo(new UpdateCoupon.Result.CodeAlreadyUsed("VERANO10"));
        assertThat(update.execute(new UpdateCoupon.Command(ADMIN, id, new CouponInput("INVIERNO",
            CouponType.PERCENTAGE, 0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))))
            .isEqualTo(new UpdateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.DISCOUNT_VALUE)));
        assertThat(coupons.findById(id)).hasValueSatisfying(coupon -> {
            assertThat(coupon.code().value()).isEqualTo("INVIERNO");
            assertThat(coupon.discountValue()).isEqualTo(10);
        });
    }

    @Test
    void givenACoupon_whenDeactivatedAndActivated_thenItsStateFollowsAndRepeatingChangesNothing() {
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10"))));
        coupons.redeem(id, 2);

        DeactivateCoupon.Result deactivated = deactivate.execute(new DeactivateCoupon.Command(ADMIN, id));
        assertThat(deactivated).isInstanceOfSatisfying(DeactivateCoupon.Result.Deactivated.class, done -> {
            assertThat(done.coupon().active()).isFalse();
            assertThat(done.coupon().uses()).isEqualTo(2);
        });
        assertThat(deactivate.execute(new DeactivateCoupon.Command(ADMIN, id))).isEqualTo(deactivated);

        ActivateCoupon.Result activated = activate.execute(new ActivateCoupon.Command(ADMIN, id));
        assertThat(activated).isInstanceOfSatisfying(ActivateCoupon.Result.Activated.class,
            done -> assertThat(done.coupon().active()).isTrue());
        assertThat(activate.execute(new ActivateCoupon.Command(ADMIN, id))).isEqualTo(activated);
    }

    @Test
    void givenACouponWithRecordedUses_whenDeletionIsAttempted_thenOnlyDeactivatingItIsAllowed() {
        CouponId used = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10"))));
        CouponId unused = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("INVIERNO"))));
        coupons.redeem(used, 1);

        assertThat(delete.execute(new DeleteCoupon.Command(ADMIN, used))).isEqualTo(new DeleteCoupon.Result.HasUses());
        assertThat(deactivate.execute(new DeactivateCoupon.Command(ADMIN, used)))
            .isInstanceOf(DeactivateCoupon.Result.Deactivated.class);
        assertThat(delete.execute(new DeleteCoupon.Command(ADMIN, unused))).isEqualTo(new DeleteCoupon.Result.Deleted());
        assertThat(coupons.findById(used)).isPresent();
        assertThat(coupons.findById(unused)).isEmpty();
    }

    @Test
    void givenAMissingCoupon_whenChangingIt_thenItIsNotFound() {
        CouponId missing = CouponId.of(UUID.randomUUID());

        assertThat(update.execute(new UpdateCoupon.Command(ADMIN, missing, percentage("VERANO10"))))
            .isEqualTo(new UpdateCoupon.Result.NotFound());
        assertThat(activate.execute(new ActivateCoupon.Command(ADMIN, missing)))
            .isEqualTo(new ActivateCoupon.Result.NotFound());
        assertThat(deactivate.execute(new DeactivateCoupon.Command(ADMIN, missing)))
            .isEqualTo(new DeactivateCoupon.Result.NotFound());
        assertThat(delete.execute(new DeleteCoupon.Command(ADMIN, missing)))
            .isEqualTo(new DeleteCoupon.Result.NotFound());
        assertThat(coupons.listNewestFirst()).isEmpty();
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenUsingAnyCouponUseCase_thenItIsForbidden() {
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("VERANO10"))));

        assertThat(list.execute(new ListCoupons.Query(CUSTOMER))).isEqualTo(new ListCoupons.Result.Forbidden());
        assertThat(create.execute(new CreateCoupon.Command(CUSTOMER, percentage("INVIERNO"))))
            .isEqualTo(new CreateCoupon.Result.Forbidden());
        assertThat(update.execute(new UpdateCoupon.Command(CUSTOMER, id, percentage("INVIERNO"))))
            .isEqualTo(new UpdateCoupon.Result.Forbidden());
        assertThat(activate.execute(new ActivateCoupon.Command(Actor.system(), id)))
            .isEqualTo(new ActivateCoupon.Result.Forbidden());
        assertThat(deactivate.execute(new DeactivateCoupon.Command(CUSTOMER, id)))
            .isEqualTo(new DeactivateCoupon.Result.Forbidden());
        assertThat(delete.execute(new DeleteCoupon.Command(Actor.anonymous(Optional.empty(), Optional.empty()), id)))
            .isEqualTo(new DeleteCoupon.Result.Forbidden());
        assertThat(coupons.findById(id)).hasValueSatisfying(coupon -> assertThat(coupon.active()).isTrue());
    }

    @Test
    void givenAnAutomaticCoupon_whenItIsCreatedWithAScopeOfOneCategory_thenTheListShowsItAsAutomaticWithThatCategory() {
        UUID category = UUID.randomUUID();

        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN,
            scoped("VERANO10", new CouponInput.Scope(Set.of(), Set.of(category))), true)));

        assertThat(list.execute(new ListCoupons.Query(ADMIN))).isInstanceOfSatisfying(ListCoupons.Result.Listed.class,
            listed -> assertThat(listed.coupons()).singleElement().satisfies(coupon -> {
                assertThat(coupon.id()).isEqualTo(id);
                assertThat(coupon.automatic()).isTrue();
                assertThat(coupon.scope()).isEqualTo(new CouponScope(Set.of(), Set.of(category)));
            }));
        assertThat(coupons.activeOldestFirst()).singleElement()
            .satisfies(counted -> assertThat(counted.coupon().id()).isEqualTo(id));
    }

    @Test
    void givenACodeCoupon_whenAScopeIsSentForIt_thenItIsRefused() {
        CouponInput.Scope scope = new CouponInput.Scope(Set.of(UUID.randomUUID()), Set.of());

        assertThat(create.execute(new CreateCoupon.Command(ADMIN, scoped("VERANO10", scope))))
            .isEqualTo(new CreateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.SCOPE)));
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN, percentage("INVIERNO"))));
        assertThat(update.execute(new UpdateCoupon.Command(ADMIN, id, scoped("INVIERNO", scope))))
            .isEqualTo(new UpdateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.SCOPE)));
        assertThat(coupons.findById(id)).hasValueSatisfying(coupon -> assertThat(coupon.scope().isWholeCart()).isTrue());
    }

    @Test
    void givenAScopeOfMoreThanOneHundredTargets_whenAnAutomaticCouponIsCreated_thenItIsRefused() {
        Set<UUID> products = Stream.generate(UUID::randomUUID).limit(60).collect(Collectors.toSet());
        Set<UUID> categories = Stream.generate(UUID::randomUUID).limit(41).collect(Collectors.toSet());

        assertThat(create.execute(new CreateCoupon.Command(ADMIN,
            scoped("VERANO10", new CouponInput.Scope(products, categories)), true)))
            .isEqualTo(new CreateCoupon.Result.InvalidFields(Set.of(CouponInput.Field.SCOPE)));
        assertThat(create.execute(new CreateCoupon.Command(ADMIN,
            scoped("VERANO10", new CouponInput.Scope(products, Set.copyOf(List.copyOf(categories).subList(0, 40)))),
            true))).isInstanceOf(CreateCoupon.Result.Created.class);
    }

    @Test
    void givenAnAutomaticCoupon_whenEditedWithAnotherScope_thenItStaysAutomaticWithTheNewScope() {
        CouponId id = idOf(create.execute(new CreateCoupon.Command(ADMIN,
            scoped("VERANO10", new CouponInput.Scope(Set.of(), Set.of(UUID.randomUUID()))), true)));
        UUID product = UUID.randomUUID();

        UpdateCoupon.Result result = update.execute(new UpdateCoupon.Command(ADMIN, id,
            scoped("VERANO10", new CouponInput.Scope(Set.of(product), Set.of()))));

        assertThat(result).isInstanceOfSatisfying(UpdateCoupon.Result.Updated.class, updated -> {
            assertThat(updated.coupon().automatic()).isTrue();
            assertThat(updated.coupon().scope()).isEqualTo(new CouponScope(Set.of(product), Set.of()));
        });
        assertThat(update.execute(new UpdateCoupon.Command(ADMIN, id, percentage("VERANO10"))))
            .isInstanceOfSatisfying(UpdateCoupon.Result.Updated.class, updated -> {
                assertThat(updated.coupon().automatic()).isTrue();
                assertThat(updated.coupon().scope()).isEqualTo(CouponScope.WHOLE_CART);
            });
    }

    private static CouponInput scoped(String code, CouponInput.Scope scope) {
        return new CouponInput(code, CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), scope);
    }

    private static CouponInput percentage(String code) {
        return new CouponInput(code, CouponType.PERCENTAGE, 10, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());
    }

    private static CouponId idOf(CreateCoupon.Result result) {
        return ((CreateCoupon.Result.Created) result).coupon().id();
    }
}
