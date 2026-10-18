package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.api.CheckoutCart;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-13: the cart the checkout turns into an order, and its emptying once the order is placed. */
class CheckoutCartsServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryCoupons coupons = new InMemoryCoupons();
    private final InMemoryItemDescriptions descriptions = new InMemoryItemDescriptions();
    private final CheckoutCartsService checkoutCarts =
        new CheckoutCartsService(carts, availability, coupons, descriptions);
    private final CartClearingService clearing =
        new CartClearingService(carts, carts, Clock.fixed(NOW, ZoneOffset.UTC));
    private Cart cart;

    /** The guest's cart: 2 pijamas at ARS 20,000 and then 1 camisón at ARS 10,000, all available. */
    @BeforeEach
    void fillCart() {
        cart = Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Guest(TOKEN.hash()), OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, Money.of(2_000_000), OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, Money.of(1_000_000), OPENED);
        carts.save(cart);
        availability.sell(PIJAMA, Money.of(2_000_000), 5);
        availability.sell(CAMISON, Money.of(1_000_000), 5);
        descriptions.add(PIJAMA,
            new ItemDescription("Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", Optional.empty()));
        descriptions.add(CAMISON,
            new ItemDescription("Camisón Sol", "camison-sol", "S", "Gris", "CS-S-GRIS", Optional.empty()));
    }

    @Test
    void givenACartWhoseItemsAreAvailable_whenLockedForCheckout_thenEveryLineIsReadyInTheCartsOrder() {
        CouponId coupon = coupons.percentage("VERANO10", 10);
        cart.applyCoupon(coupon, OPENED);
        carts.save(cart);

        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.Ready(List.of(
                new CheckoutCart.Line(PIJAMA.value(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
                new CheckoutCart.Line(CAMISON.value(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000),
                    1)),
            Money.of(5_000_000),
            Optional.of(new CheckoutCart.Coupon(coupon.value(), "VERANO10", CouponStatus.APPLICABLE)),
            Optional.of(new CheckoutCart.AppliedDiscount(coupon.value(), "VERANO10", Money.of(500_000)))));
    }

    @Test
    void givenAnAutomaticCouponThatApplies_whenLockedForCheckout_thenItIsTheAppliedDiscountWithoutACode() {
        CouponId automatic = coupons.automatic("OTONO10", 10, Money.of(3_000_000));

        var ready = (CheckoutCart.Ready) checkoutCarts.lock(GUEST);

        assertThat(ready.coupon()).isEmpty();
        assertThat(ready.applied())
            .contains(new CheckoutCart.AppliedDiscount(automatic.value(), "OTONO10", Money.of(500_000)));
        assertThat(ready.discount()).isEqualTo(Money.of(500_000));
    }

    @Test
    void givenAHeldCodeThatDoesNotApplyAndAnAutomaticCouponThatDoes_whenLockedForCheckout_thenBothComeAsTheyAre() {
        CouponId automatic = coupons.automatic("OTONO10", 10, Money.of(1));
        CouponId code = coupons.percentage("VERANO20", 20);
        coupons.reject(code, CouponCheck.Status.EXPIRED);
        cart.applyCoupon(code, OPENED);
        carts.save(cart);

        var ready = (CheckoutCart.Ready) checkoutCarts.lock(GUEST);

        // The checkout refuses it (UC-13): the held code no longer applies.
        assertThat(ready.coupon()).contains(new CheckoutCart.Coupon(code.value(), "VERANO20", CouponStatus.EXPIRED));
        assertThat(ready.applied()).map(CheckoutCart.AppliedDiscount::couponId).contains(automatic.value());
    }

    @Test
    void givenACouponThatNoLongerApplies_whenLockedForCheckout_thenItComesWithItsStatusAndNoDiscount() {
        CouponId coupon = coupons.percentage("VERANO10", 10);
        coupons.reject(coupon, CouponCheck.Status.EXPIRED);
        cart.applyCoupon(coupon, OPENED);
        carts.save(cart);

        var ready = (CheckoutCart.Ready) checkoutCarts.lock(GUEST);

        assertThat(ready.discount()).isEqualTo(Money.zero());
        assertThat(ready.coupon()).contains(new CheckoutCart.Coupon(coupon.value(), "VERANO10", CouponStatus.EXPIRED));
    }

    @Test
    void givenACouponThatNoLongerExists_whenLockedForCheckout_thenTheCartHasNone() {
        CouponId coupon = coupons.percentage("VERANO10", 10);
        cart.applyCoupon(coupon, OPENED);
        carts.save(cart);
        coupons.delete(coupon);

        assertThat(((CheckoutCart.Ready) checkoutCarts.lock(GUEST)).coupon()).isEmpty();
    }

    @Test
    void givenAnItemOutOfStock_whenLockedForCheckout_thenTheCartIsNotReady() {
        availability.sell(CAMISON, Money.of(1_000_000), 0);

        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.NotReady());
    }

    @Test
    void givenAnItemWithFewerUnitsLeftOrNoLongerSold_whenLockedForCheckout_thenTheCartIsNotReady() {
        availability.sell(PIJAMA, Money.of(2_000_000), 1);
        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.NotReady());

        availability.sell(PIJAMA, Money.of(2_000_000), 5);
        availability.stopSelling(CAMISON);
        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.NotReady());
    }

    @Test
    void givenAnUnacknowledgedPriceChange_whenLockedForCheckout_thenTheCartIsNotReady() {
        availability.sell(PIJAMA, Money.of(2_200_000), 5);

        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.NotReady());
    }

    @Test
    void givenNoCartNoItemsOrAnActorThatCannotOwnOne_whenLockedForCheckout_thenTheCartIsEmpty() {
        assertThat(checkoutCarts.lock(Actor.anonymous(Optional.of(new CartToken("another-token")), Optional.empty())))
            .isEqualTo(new CheckoutCart.Empty());
        assertThat(checkoutCarts.lock(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new CheckoutCart.Empty());
        assertThat(checkoutCarts.lock(Actor.admin(UserId.of(UUID.randomUUID())))).isEqualTo(new CheckoutCart.Empty());
        assertThat(checkoutCarts.lock(Actor.system())).isEqualTo(new CheckoutCart.Empty());

        clearing.clear(GUEST);
        assertThat(checkoutCarts.lock(GUEST)).isEqualTo(new CheckoutCart.Empty());
    }

    @Test
    void givenAPlacedOrder_whenItsCartIsCleared_thenTheCartKeepsNoItemsAndNoCoupon() {
        cart.applyCoupon(coupons.percentage("VERANO10", 10), OPENED);
        carts.save(cart);

        clearing.clear(GUEST);

        Cart cleared = carts.findByOwner(new CartOwner.Guest(TOKEN.hash())).orElseThrow();
        assertThat(cleared.items()).isEmpty();
        assertThat(cleared.coupon()).isEmpty();
        assertThat(cleared.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnActorWithoutACart_whenClearingIt_thenNothingHappens() {
        clearing.clear(Actor.anonymous(Optional.of(new CartToken("another-token")), Optional.empty()));
        clearing.clear(Actor.system());

        assertThat(carts.findByOwner(new CartOwner.Guest(TOKEN.hash())).orElseThrow().items()).hasSize(2);
    }
}
