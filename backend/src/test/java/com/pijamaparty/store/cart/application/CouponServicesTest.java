package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.application.CartView.CouponView;
import com.pijamaparty.store.cart.application.CartView.ItemView;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.cart.domain.PricedCart.LineStatus;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-10: {@link ApplyCoupon}, {@link RemoveCoupon}, and the coupon in every cart view. */
class CouponServicesTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final Actor OTHER_GUEST = Actor.anonymous(Optional.of(new CartToken("another-token")),
        Optional.empty());
    private static final Actor GUEST_WITHOUT_TOKEN = Actor.anonymous(Optional.empty(), Optional.empty());
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final CartOwner GUEST_OWNER = new CartOwner.Guest(TOKEN.hash());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final CartItemId PIJAMA_ITEM = CartItemId.of(UUID.randomUUID());
    private static final Money PRICE = Money.of(2_000_000);

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryCoupons coupons = new InMemoryCoupons();
    private final InMemoryItemDescriptions descriptions = new InMemoryItemDescriptions();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ApplyCouponService applyCoupon =
        new ApplyCouponService(carts, carts, availability, coupons, descriptions, clock);
    private final RemoveCouponService removeCoupon =
        new RemoveCouponService(carts, carts, availability, coupons, descriptions, clock);
    private final GetCartService getCart = new GetCartService(carts, availability, coupons, descriptions);
    private final ChangeItemQuantityService changeQuantity =
        new ChangeItemQuantityService(carts, carts, availability, coupons, descriptions, clock);

    /** The guest's cart holds 1 pijama at ARS 20,000; 5 are available. */
    @BeforeEach
    void fillCart() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST_OWNER, OPENED);
        cart.add(PIJAMA_ITEM, PIJAMA, 1, 5, PRICE, OPENED);
        carts.save(cart);
        availability.sell(PIJAMA, PRICE, 5);
    }

    @Test
    void givenAValid10PercentCoupon_whenAppliedToA20000Cart_thenTheDiscountIs2000() {
        CouponId verano = coupons.percentage("VERANO10", 10);

        var result = apply(GUEST, "verano10");

        CartView expected = cart(Optional.of(applicable("VERANO10")), Money.of(200_000));
        assertThat(result).isEqualTo(new ApplyCoupon.Result.Applied(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        Cart stored = carts.findByOwner(GUEST_OWNER).orElseThrow();
        assertThat(stored.coupon()).contains(verano);
        assertThat(stored.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenACouponAlreadyApplied_whenAnotherIsApplied_thenItReplacesIt() {
        coupons.percentage("VERANO10", 10);
        CouponId pijama = coupons.percentage("PIJAMA25", 25);
        apply(GUEST, "VERANO10");

        var result = apply(GUEST, "PIJAMA25");

        assertThat(result).isEqualTo(new ApplyCoupon.Result.Applied(
            cart(Optional.of(applicable("PIJAMA25")), Money.of(500_000))));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().coupon()).contains(pijama);
    }

    @Test
    void givenACouponThatDoesNotApply_whenApplied_thenEachReasonIsItsOwnResultAndTheCartKeepsItsCoupon() {
        CouponId verano = coupons.percentage("VERANO10", 10);
        apply(GUEST, "VERANO10");
        reject("VENCIDO", CouponCheck.Status.EXPIRED);
        reject("APAGADO", CouponCheck.Status.INACTIVE);
        reject("NAVIDAD", CouponCheck.Status.NOT_STARTED);
        reject("AGOTADO", CouponCheck.Status.USED_UP);

        assertThat(apply(GUEST, "VENCIDO")).isEqualTo(new ApplyCoupon.Result.Expired());
        assertThat(apply(GUEST, "APAGADO")).isEqualTo(new ApplyCoupon.Result.Inactive());
        assertThat(apply(GUEST, "NAVIDAD")).isEqualTo(new ApplyCoupon.Result.NotStarted());
        assertThat(apply(GUEST, "AGOTADO")).isEqualTo(new ApplyCoupon.Result.UsedUp());
        assertThat(apply(GUEST, "NO-EXISTE")).isEqualTo(new ApplyCoupon.Result.Unknown());

        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().coupon()).contains(verano);
    }

    @Test
    void givenACartBelowTheMinimum_whenTheCouponIsApplied_thenTheUserIsToldHowMuchIsMissing() {
        coupons.percentage("MINIMO", 10, Money.of(3_000_000));

        assertThat(apply(GUEST, "MINIMO")).isEqualTo(new ApplyCoupon.Result.BelowMinimum(Money.of(1_000_000)));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().coupon()).isEmpty();
    }

    @Test
    void givenNoCartOrACartWithoutItems_whenApplyingACoupon_thenTheCartIsEmptyAndNoneIsCreated() {
        coupons.percentage("VERANO10", 10);
        changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 0));

        assertThat(apply(OTHER_GUEST, "VERANO10")).isEqualTo(new ApplyCoupon.Result.EmptyCart());
        assertThat(apply(GUEST, "VERANO10")).isEqualTo(new ApplyCoupon.Result.EmptyCart());
        assertThat(carts.size()).isEqualTo(1);
    }

    @Test
    void givenAnActorThatCannotOwnACart_whenApplyingOrRemovingACoupon_thenItIsForbidden() {
        coupons.percentage("VERANO10", 10);

        assertThat(apply(ADMIN, "VERANO10")).isEqualTo(new ApplyCoupon.Result.Forbidden());
        assertThat(apply(GUEST_WITHOUT_TOKEN, "VERANO10")).isEqualTo(new ApplyCoupon.Result.Forbidden());
        assertThat(removeCoupon.execute(new RemoveCoupon.Command(ADMIN)))
            .isEqualTo(new RemoveCoupon.Result.Forbidden());
    }

    @Test
    void givenAnAppliedCouponWithAMinimum_whenTheCartFallsBelowIt_thenItStaysWithoutDiscountUntilTheCartQualifies() {
        coupons.percentage("MINIMO", 10, Money.of(2_000_000));
        apply(GUEST, "MINIMO");

        availability.stopSelling(PIJAMA);
        CartView below = getCart.execute(new GetCart.Query(GUEST));
        assertThat(below.coupon()).contains(
            new CouponView("MINIMO", CouponCheck.Status.BELOW_MINIMUM, Money.of(2_000_000)));
        assertThat(below.discount()).isEqualTo(Money.zero());
        assertThat(below.total()).isEqualTo(Money.zero());

        availability.sell(PIJAMA, PRICE, 5);
        CartView qualifies = getCart.execute(new GetCart.Query(GUEST));
        assertThat(qualifies.coupon()).contains(applicable("MINIMO"));
        assertThat(qualifies.discount()).isEqualTo(Money.of(200_000));
    }

    @Test
    void givenAnAppliedCouponThatExpires_whenTheCartChanges_thenTheCouponShowsWhyAndDiscountsNothing() {
        CouponId verano = coupons.percentage("VERANO10", 10);
        apply(GUEST, "VERANO10");
        coupons.reject(verano, CouponCheck.Status.EXPIRED);

        var result = changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 2));

        CartView changed = ((ChangeItemQuantity.Result.Changed) result).cart();
        assertThat(changed.coupon()).contains(new CouponView("VERANO10", CouponCheck.Status.EXPIRED, Money.zero()));
        assertThat(changed.discount()).isEqualTo(Money.zero());
        assertThat(changed.total()).isEqualTo(Money.of(4_000_000));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().coupon()).contains(verano);
    }

    @Test
    void givenAnAppliedCouponThatNoLongerExists_whenGettingTheCart_thenItShowsNoCoupon() {
        CouponId verano = coupons.percentage("VERANO10", 10);
        apply(GUEST, "VERANO10");
        coupons.delete(verano);

        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(cart(Optional.empty(), Money.zero()));
    }

    @Test
    void givenAnAppliedCoupon_whenRemovingIt_thenTheCartHasNoCouponOrDiscount() {
        coupons.percentage("VERANO10", 10);
        apply(GUEST, "VERANO10");

        var result = removeCoupon.execute(new RemoveCoupon.Command(GUEST));

        CartView expected = cart(Optional.empty(), Money.zero());
        assertThat(result).isEqualTo(new RemoveCoupon.Result.Removed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().coupon()).isEmpty();
    }

    @Test
    void givenNoCouponOrNoCart_whenRemovingTheCoupon_thenNothingChanges() {
        assertThat(removeCoupon.execute(new RemoveCoupon.Command(GUEST)))
            .isEqualTo(new RemoveCoupon.Result.Removed(cart(Optional.empty(), Money.zero())));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(OPENED);
        assertThat(removeCoupon.execute(new RemoveCoupon.Command(OTHER_GUEST)))
            .isEqualTo(new RemoveCoupon.Result.Removed(CartView.empty()));
        assertThat(carts.size()).isEqualTo(1);
    }

    private ApplyCoupon.Result apply(Actor actor, String code) {
        return applyCoupon.execute(new ApplyCoupon.Command(actor, code));
    }

    private void reject(String code, CouponCheck.Status reason) {
        coupons.reject(coupons.percentage(code, 10), reason);
    }

    private static CouponView applicable(String code) {
        return new CouponView(code, CouponCheck.Status.APPLICABLE, Money.zero());
    }

    /** The guest's cart, 1 pijama at ARS 20,000, with {@code coupon} and its {@code discount}. */
    private static CartView cart(Optional<CouponView> coupon, Money discount) {
        ItemView pijama =
            new ItemView(PIJAMA_ITEM, PIJAMA, 1, PRICE, Optional.empty(), LineStatus.AVAILABLE, 5, Optional.empty());
        return new CartView(List.of(pijama), coupon, PRICE, discount, PRICE.minus(discount));
    }
}
