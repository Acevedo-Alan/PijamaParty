package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadCart}, {@link SaveCart}, {@link DeleteCart} and {@link DeleteIdleCarts} over the same storage.
 * Owners are the customer and the guests below, so a real adapter can act as each of them (Row Level Security,
 * RNF-27); it deletes idle carts as the system, like the daily cleanup, and a cart as the customer signing in with
 * {@link #GUEST_TOKEN}, like the merge of UC-19.
 */
public abstract class CartStoreContractTest<T extends LoadCart & SaveCart & DeleteCart & DeleteIdleCarts>
    extends ContractTest<T> {

    public static final CartToken GUEST_TOKEN = RlsPolicyTester.GUEST_CART;
    public static final CartToken OTHER_GUEST_TOKEN = new CartToken("another-guest-cart-token");
    public static final CartOwner GUEST = new CartOwner.Guest(GUEST_TOKEN.hash());
    public static final CartOwner OTHER_GUEST = new CartOwner.Guest(OTHER_GUEST_TOKEN.hash());
    public static final CartOwner CUSTOMER = new CartOwner.Customer(RlsPolicyTester.CUSTOMER_A_ID);

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Money PRICE = Money.of(1_500_000);

    @Test
    void givenASavedCartWithItems_whenLoadingItsOwnersCart_thenItHasTheSameStateAndOrder() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        // Ids in descending order, so only the time they were added can explain the order.
        cart.add(CartItemId.of(new UUID(9, 9)), VariantId.of(UUID.randomUUID()), 2, 5, PRICE, OPENED);
        cart.add(CartItemId.of(new UUID(1, 1)), VariantId.of(UUID.randomUUID()), 1, 5, Money.of(990_000),
            OPENED.plusSeconds(60));

        subject.save(cart);
        persistAndForget();

        assertSameState(subject.findByOwner(GUEST), cart);
        assertSameState(subject.lockByOwner(GUEST), cart);
    }

    @Test
    void givenACartWithACoupon_whenTheCouponIsAppliedAndThenRemoved_thenEachSaveKeepsIt() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, PRICE, OPENED);
        CouponId coupon = CouponId.of(UUID.randomUUID());
        cart.applyCoupon(coupon, OPENED.plusSeconds(60));
        subject.save(cart);
        persistAndForget();

        Cart loaded = subject.lockByOwner(GUEST).orElseThrow();
        assertSameState(Optional.of(loaded), cart);
        assertThat(loaded.coupon()).contains(coupon);

        loaded.removeCoupon(OPENED.plusSeconds(120));
        subject.save(loaded);
        persistAndForget();

        assertSameState(subject.findByOwner(GUEST), loaded);
        assertThat(subject.findByOwner(GUEST).orElseThrow().coupon()).isEmpty();
    }

    @Test
    void givenACustomersCart_whenLoadingIt_thenItIsFound() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 3, 5, PRICE, OPENED);

        subject.save(cart);
        persistAndForget();

        assertSameState(subject.findByOwner(CUSTOMER), cart);
    }

    @Test
    void givenNoCart_whenLoadingIt_thenNothingIsFound() {
        assertThat(subject.findByOwner(GUEST)).isEmpty();
        assertThat(subject.lockByOwner(CUSTOMER)).isEmpty();
    }

    @Test
    void givenAnotherGuestsCart_whenLoadingTheCartOfAGuest_thenNothingIsFound() {
        subject.save(Cart.open(CartId.of(UUID.randomUUID()), OTHER_GUEST, OPENED));
        persistAndForget();

        assertThat(subject.findByOwner(GUEST)).isEmpty();
        assertThat(subject.lockByOwner(GUEST)).isEmpty();
    }

    @Test
    void givenACartWithoutItems_whenSaved_thenItIsFoundEmpty() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);

        subject.save(cart);
        persistAndForget();

        assertSameState(subject.findByOwner(GUEST), cart);
    }

    @Test
    void givenAChangedCart_whenSavedAgain_thenItsItemsAreExactlyTheNewOnes() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        VariantId kept = VariantId.of(UUID.randomUUID());
        CartItemId keptId = CartItemId.of(UUID.randomUUID());
        CartItemId removedId = CartItemId.of(UUID.randomUUID());
        cart.add(keptId, kept, 1, 5, PRICE, OPENED);
        cart.add(removedId, VariantId.of(UUID.randomUUID()), 1, 5, PRICE, OPENED);
        subject.save(cart);
        persistAndForget();

        Cart loaded = subject.lockByOwner(GUEST).orElseThrow();
        Instant later = OPENED.plusSeconds(3600);
        loaded.add(CartItemId.of(UUID.randomUUID()), kept, 2, 5, Money.of(1_400_000), later);
        loaded.remove(removedId, later);
        loaded.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 4, 5, PRICE, later);
        subject.save(loaded);
        persistAndForget();

        Cart reloaded = subject.findByOwner(GUEST).orElseThrow();
        assertSameState(Optional.of(reloaded), loaded);
        assertThat(reloaded.item(keptId)).map(CartItem::quantity).contains(3);
        assertThat(reloaded.item(keptId)).map(CartItem::lastSeenPrice).contains(Money.of(1_400_000));
        assertThat(reloaded.updatedAt()).isEqualTo(later);
        assertThat(reloaded.createdAt()).isEqualTo(OPENED);
    }

    @Test
    void givenACartThatIsNotSaved_whenLoadingItAgain_thenTheChangeIsNotThere() {
        subject.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED));
        persistAndForget();

        subject.lockByOwner(GUEST).orElseThrow()
            .add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, PRICE, OPENED);
        persistAndForget();

        assertThat(subject.findByOwner(GUEST).orElseThrow().items()).isEmpty();
    }

    @Test
    void givenCartsChangedBeforeAndAtTheCutoff_whenDeletingIdleCarts_thenOnlyTheEarlierOnesGoWithTheirItems() {
        Instant cutoff = OPENED.plusSeconds(3600);
        Cart idleGuestCart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        idleGuestCart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, PRICE, OPENED);
        Cart idleCustomerCart = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, cutoff.minusSeconds(1));
        Cart activeCart = Cart.open(CartId.of(UUID.randomUUID()), OTHER_GUEST, OPENED);
        activeCart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, PRICE, cutoff);
        subject.save(idleGuestCart);
        subject.save(idleCustomerCart);
        subject.save(activeCart);
        persistAndForget();

        assertThat(subject.deleteUpdatedBefore(cutoff)).isEqualTo(2);
        persistAndForget();

        assertThat(subject.findByOwner(GUEST)).isEmpty();
        assertThat(subject.findByOwner(CUSTOMER)).isEmpty();
        assertSameState(subject.findByOwner(OTHER_GUEST), activeCart);
        assertThat(subject.deleteUpdatedBefore(cutoff)).isZero();
    }

    @Test
    void givenADeletedIdleCart_whenItsOwnerAddsAgain_thenANewCartIsSaved() {
        subject.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED));
        persistAndForget();
        subject.deleteUpdatedBefore(OPENED.plusSeconds(1));
        persistAndForget();

        Cart fresh = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED.plusSeconds(60));
        subject.save(fresh);
        persistAndForget();

        assertSameState(subject.findByOwner(GUEST), fresh);
    }

    @Test
    void givenTheGuestCartWithItems_whenDeleted_thenItGoesWithItsItemsAndTheOtherCartsStay() {
        Cart guestCart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        guestCart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 2, 5, PRICE, OPENED);
        Cart customerCart = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        customerCart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, PRICE, OPENED);
        subject.save(guestCart);
        subject.save(customerCart);
        persistAndForget();

        assertThat(subject.delete(guestCart.id())).isTrue();
        persistAndForget();

        assertThat(subject.findByOwner(GUEST)).isEmpty();
        assertSameState(subject.findByOwner(CUSTOMER), customerCart);
    }

    @Test
    void givenADeletedGuestCart_whenItsLinesAreSavedInTheCustomersCart_thenTheyKeepTheirIds() {
        CartItemId line = CartItemId.of(UUID.randomUUID());
        Cart guestCart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        guestCart.add(line, VariantId.of(UUID.randomUUID()), 2, 5, PRICE, OPENED);
        subject.save(guestCart);
        persistAndForget();

        assertThat(subject.delete(guestCart.id())).isTrue();
        Cart customerCart = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        customerCart.merge(guestCart, Map.of(guestCart.items().getFirst().variantId(),
            new SellableVariant(PRICE, 5)), OPENED.plusSeconds(60));
        subject.save(customerCart);
        persistAndForget();

        assertSameState(subject.findByOwner(CUSTOMER), customerCart);
        assertThat(subject.findByOwner(CUSTOMER).orElseThrow().item(line)).isPresent();
    }

    @Test
    void givenNoCart_whenDeletingOne_thenNothingHappens() {
        subject.save(Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED));
        persistAndForget();

        assertThat(subject.delete(CartId.of(UUID.randomUUID()))).isFalse();
        persistAndForget();

        assertThat(subject.findByOwner(CUSTOMER)).isPresent();
    }

    private static void assertSameState(Optional<Cart> loaded, Cart expected) {
        assertThat(loaded).hasValueSatisfying(cart -> assertThat(cart).usingRecursiveComparison().isEqualTo(expected));
    }
}
