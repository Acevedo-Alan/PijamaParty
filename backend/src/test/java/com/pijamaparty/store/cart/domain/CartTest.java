package com.pijamaparty.store.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant LATER = OPENED.plusSeconds(60);
    private static final CartOwner GUEST = new CartOwner.Guest(new CartToken("a-guest-cart-token").hash());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());
    private static final Money PRICE = Money.of(1_500_000);

    private final Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);

    @Test
    void givenANewCart_whenAddingAVariant_thenItHasOneLineWithThatQuantityAndPrice() {
        CartItemId id = CartItemId.of(UUID.randomUUID());

        var outcome = cart.add(id, PIJAMA, 2, 5, PRICE, LATER);

        CartItem line = new CartItem(id, PIJAMA, 2, PRICE, LATER);
        assertThat(outcome).isEqualTo(new Cart.AddOutcome.Added(line));
        assertThat(cart.items()).containsExactly(line);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
        assertThat(cart.createdAt()).isEqualTo(OPENED);
    }

    @Test
    void givenAVariantInTheCart_whenAddingItAgain_thenItsLineAddsTheQuantityAndRemembersTheNewPrice() {
        CartItemId id = CartItemId.of(UUID.randomUUID());
        cart.add(id, PIJAMA, 2, 5, PRICE, OPENED);

        var outcome = cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 3, 5, Money.of(1_400_000), LATER);

        CartItem line = new CartItem(id, PIJAMA, 5, Money.of(1_400_000), OPENED);
        assertThat(outcome).isEqualTo(new Cart.AddOutcome.Added(line));
        assertThat(cart.items()).containsExactly(line);
    }

    @Test
    void givenAVariantWithStock3And2UnitsInTheCart_whenAdding2More_thenItIsRejectedWith3Left() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 3, PRICE, OPENED);

        var outcome = cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 3, PRICE, LATER);

        assertThat(outcome).isEqualTo(new Cart.AddOutcome.ExceedsStock(3));
        assertThat(cart.items()).singleElement().extracting(CartItem::quantity).isEqualTo(2);
        assertThat(cart.updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenAVariantWithoutStock_whenAddingIt_thenItIsRejected() {
        assertThat(cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 0, PRICE, LATER))
            .isEqualTo(new Cart.AddOutcome.ExceedsStock(0));
        assertThat(cart.items()).isEmpty();
    }

    @Test
    void givenSeveralVariants_whenAdded_thenTheLinesKeepTheOrderTheyWereAddedIn() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 5, PRICE, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, PRICE, LATER);
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 5, PRICE, LATER.plusSeconds(1));

        assertThat(cart.items()).extracting(CartItem::variantId).containsExactly(PIJAMA, CAMISON);
    }

    @Test
    void givenAQuantityOutOfRange_whenAdding_thenItFails() {
        assertThatThrownBy(() -> cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 0, 5, PRICE, LATER))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, Cart.MAX_QUANTITY + 1,
            Cart.MAX_QUANTITY, PRICE, LATER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenALineAtTheMaximum_whenAddingMore_thenItIsRejectedWithoutOverflowing() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, Cart.MAX_QUANTITY, Cart.MAX_QUANTITY, PRICE, OPENED);

        assertThat(cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, Cart.MAX_QUANTITY, Integer.MAX_VALUE, PRICE,
            LATER)).isEqualTo(new Cart.AddOutcome.ExceedsStock(Integer.MAX_VALUE));
    }

    @Test
    void givenAnItem_whenChangingItsQuantityWithinTheStock_thenItChangesAndKeepsItsPrice() {
        CartItemId id = CartItemId.of(UUID.randomUUID());
        cart.add(id, PIJAMA, 2, 5, PRICE, OPENED);

        var outcome = cart.changeQuantity(id, 4, 4, LATER);

        CartItem line = new CartItem(id, PIJAMA, 4, PRICE, OPENED);
        assertThat(outcome).isEqualTo(new Cart.ChangeOutcome.Changed(line));
        assertThat(cart.items()).containsExactly(line);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenAnItem_whenItsQuantityIsChangedTo0_thenItIsRemovedWhateverTheStock() {
        CartItemId id = CartItemId.of(UUID.randomUUID());
        cart.add(id, PIJAMA, 2, 5, PRICE, OPENED);

        assertThat(cart.changeQuantity(id, 0, 0, LATER)).isEqualTo(new Cart.ChangeOutcome.Removed());
        assertThat(cart.items()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenAnItem_whenChangingItsQuantityAboveTheStock_thenItIsRejectedAndUnchanged() {
        CartItemId id = CartItemId.of(UUID.randomUUID());
        cart.add(id, PIJAMA, 2, 5, PRICE, OPENED);

        assertThat(cart.changeQuantity(id, 4, 3, LATER)).isEqualTo(new Cart.ChangeOutcome.ExceedsStock(3));
        assertThat(cart.items()).singleElement().extracting(CartItem::quantity).isEqualTo(2);
        assertThat(cart.updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenAnUnknownItem_whenChangingOrRemovingIt_thenNothingChanges() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, PRICE, OPENED);
        CartItemId unknown = CartItemId.of(UUID.randomUUID());

        assertThat(cart.changeQuantity(unknown, 1, 5, LATER)).isEqualTo(new Cart.ChangeOutcome.NoSuchItem());
        assertThat(cart.remove(unknown, LATER)).isFalse();
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenAnItem_whenRemovingIt_thenTheCartNoLongerHasIt() {
        CartItemId id = CartItemId.of(UUID.randomUUID());
        cart.add(id, PIJAMA, 2, 5, PRICE, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, PRICE, OPENED);

        assertThat(cart.remove(id, LATER)).isTrue();
        assertThat(cart.items()).extracting(CartItem::variantId).containsExactly(CAMISON);
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenPriceChanges_whenAcknowledgingThem_thenEverySoldLineRemembersItsCurrentPrice() {
        CartItemId pijama = CartItemId.of(UUID.randomUUID());
        CartItemId camison = CartItemId.of(UUID.randomUUID());
        cart.add(pijama, PIJAMA, 2, 5, PRICE, OPENED);
        cart.add(camison, CAMISON, 1, 5, PRICE, OPENED);

        boolean changed = cart.acknowledgePrices(Map.of(PIJAMA, new SellableVariant(Money.of(1_600_000), 0)), LATER);

        assertThat(changed).isTrue();
        assertThat(cart.items()).containsExactly(
            new CartItem(pijama, PIJAMA, 2, Money.of(1_600_000), OPENED),
            new CartItem(camison, CAMISON, 1, PRICE, OPENED));
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenNoPriceChange_whenAcknowledgingPrices_thenNothingChanges() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, PRICE, OPENED);

        assertThat(cart.acknowledgePrices(Map.of(PIJAMA, new SellableVariant(PRICE, 5)), LATER)).isFalse();
        assertThat(cart.updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenACart_whenCopied_thenChangingTheCopyLeavesTheOriginalAlone() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, PRICE, OPENED);

        Cart copy = cart.copy();
        copy.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, PRICE, LATER);

        assertThat(cart.items()).hasSize(1);
        assertThat(copy.items()).hasSize(2);
    }

    @Test
    void givenACart_whenApplyingACouponAndThenAnother_thenTheLastOneReplacesTheFirst() {
        CouponId first = CouponId.of(UUID.randomUUID());
        CouponId second = CouponId.of(UUID.randomUUID());

        cart.applyCoupon(first, LATER);
        assertThat(cart.coupon()).contains(first);
        cart.applyCoupon(second, LATER.plusSeconds(60));

        assertThat(cart.coupon()).contains(second);
        assertThat(cart.updatedAt()).isEqualTo(LATER.plusSeconds(60));
    }

    @Test
    void givenACartWithACoupon_whenRemovingIt_thenItHasNoneAndRemovingAgainChangesNothing() {
        cart.applyCoupon(CouponId.of(UUID.randomUUID()), OPENED);

        assertThat(cart.removeCoupon(LATER)).isTrue();
        assertThat(cart.coupon()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
        assertThat(cart.removeCoupon(LATER.plusSeconds(60))).isFalse();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenACartWithItemsAndACoupon_whenClearedByItsOrder_thenItHasNeitherAndClearingAgainChangesNothing() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 5, PRICE, OPENED);
        cart.applyCoupon(CouponId.of(UUID.randomUUID()), OPENED);

        assertThat(cart.clear(LATER)).isTrue();
        assertThat(cart.items()).isEmpty();
        assertThat(cart.coupon()).isEmpty();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
        assertThat(cart.clear(LATER.plusSeconds(60))).isFalse();
        assertThat(cart.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenACartWithACoupon_whenCopied_thenTheCopyHasItToo() {
        CouponId coupon = CouponId.of(UUID.randomUUID());
        cart.applyCoupon(coupon, LATER);

        assertThat(cart.copy().coupon()).contains(coupon);
    }

    @Test
    void givenTwoLinesOfOneVariant_whenRebuildingTheCart_thenItFails() {
        var first = new CartItem(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, PRICE, OPENED);
        var second = new CartItem(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, PRICE, OPENED);

        assertThatThrownBy(() -> Cart.of(CartId.of(UUID.randomUUID()), GUEST, List.of(first, second), Optional.empty(),
            OPENED, OPENED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenEachActor_whenFindingItsCartOwner_thenOnlyCustomersAndGuestsWithATokenHaveOne() {
        UserId customer = UserId.of(UUID.randomUUID());
        CartToken token = new CartToken("a-guest-cart-token");

        assertThat(CartOwner.of(Actor.customer(customer, Optional.of(token), Optional.empty())))
            .contains(new CartOwner.Customer(customer));
        assertThat(CartOwner.of(Actor.anonymous(Optional.of(token), Optional.empty())))
            .contains(new CartOwner.Guest(token.hash()));
        assertThat(CartOwner.of(Actor.anonymous(Optional.empty(), Optional.empty()))).isEmpty();
        assertThat(CartOwner.of(Actor.admin(UserId.of(UUID.randomUUID())))).isEmpty();
        assertThat(CartOwner.of(Actor.system())).isEmpty();
    }

    // --- UC-19 (T-E4-04): the guest cart joins the customer's at sign-in ---

    private static final CartOwner CUSTOMER = new CartOwner.Customer(UserId.of(UUID.randomUUID()));
    private static final VariantId BATA = VariantId.of(UUID.randomUUID());

    @Test
    void givenAVariantInBothCarts_whenMerging_thenTheQuantitiesAddUpOnTheCustomersLineWithItsPrice() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        CartItemId ownLine = CartItemId.of(UUID.randomUUID());
        own.add(ownLine, PIJAMA, 2, 10, PRICE, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 3, 10, Money.of(1_400_000), LATER);

        boolean changed = own.merge(cart, Map.of(PIJAMA, new SellableVariant(Money.of(1_400_000), 10)),
            LATER.plusSeconds(1));

        assertThat(changed).isTrue();
        assertThat(own.items()).containsExactly(new CartItem(ownLine, PIJAMA, 5, PRICE, OPENED));
        assertThat(own.updatedAt()).isEqualTo(LATER.plusSeconds(1));
    }

    @Test
    void givenAVariantInBothCartsAboveTheStock_whenMerging_thenTheLineIsCappedButNeverLowered() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        own.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 10, PRICE, OPENED);
        own.add(CartItemId.of(UUID.randomUUID()), CAMISON, 4, 10, PRICE, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 3, 10, PRICE, LATER);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 10, PRICE, LATER);

        own.merge(cart, Map.of(PIJAMA, new SellableVariant(PRICE, 4), CAMISON, new SellableVariant(PRICE, 3)), LATER);

        assertThat(own.items()).extracting(CartItem::variantId, CartItem::quantity)
            .containsExactly(tuple(PIJAMA, 4), tuple(CAMISON, 4));
    }

    @Test
    void givenAVariantOnlyInTheGuestCart_whenMerging_thenItsLineComesCappedAtTheStockInTheOrderItWasAdded() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        own.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 10, PRICE, OPENED.plusSeconds(120));
        CartItemId guestLine = CartItemId.of(UUID.randomUUID());
        cart.add(guestLine, CAMISON, 5, 10, Money.of(990_000), OPENED.plusSeconds(60));

        own.merge(cart, Map.of(CAMISON, new SellableVariant(PRICE, 3)), LATER);

        assertThat(own.items()).extracting(CartItem::variantId).containsExactly(CAMISON, PIJAMA);
        assertThat(own.item(guestLine))
            .contains(new CartItem(guestLine, CAMISON, 3, Money.of(990_000), OPENED.plusSeconds(60)));
    }

    @Test
    void givenGuestLinesTheShopNoLongerSellsOrWithoutStock_whenMerging_thenTheyAreLeftOutAndTheCustomersStay() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        own.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 10, PRICE, OPENED);
        CartItem ownLine = own.items().getFirst();
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, 10, PRICE, LATER);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 10, PRICE, LATER);
        cart.add(CartItemId.of(UUID.randomUUID()), BATA, 1, 10, PRICE, LATER);

        boolean changed = own.merge(cart, Map.of(PIJAMA, new SellableVariant(PRICE, 0)), LATER);

        assertThat(changed).isFalse();
        assertThat(own.items()).containsExactly(ownLine);
        assertThat(own.updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenCoupons_whenMerging_thenTheCustomersStaysAndTheGuestsIsTakenOnlyWhenItHasNone() {
        CouponId guestCoupon = CouponId.of(UUID.randomUUID());
        CouponId ownCoupon = CouponId.of(UUID.randomUUID());
        cart.applyCoupon(guestCoupon, OPENED);
        Cart withCoupon = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        withCoupon.applyCoupon(ownCoupon, OPENED);
        Cart withoutCoupon = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);

        assertThat(withCoupon.merge(cart, Map.of(), LATER)).isFalse();
        assertThat(withoutCoupon.merge(cart, Map.of(), LATER)).isTrue();

        assertThat(withCoupon.coupon()).contains(ownCoupon);
        assertThat(withoutCoupon.coupon()).contains(guestCoupon);
    }

    @Test
    void givenCartsOfTheWrongOwners_whenMerging_thenItFails() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        Cart otherGuest = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);

        assertThatThrownBy(() -> cart.merge(otherGuest, Map.of(), LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> own.merge(own.copy(), Map.of(), LATER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenSomethingThatIsNotATokenHash_whenMakingAGuestOwner_thenItFails() {
        assertThatThrownBy(() -> new CartOwner.Guest("a-guest-cart-token")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartOwner.Guest(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
