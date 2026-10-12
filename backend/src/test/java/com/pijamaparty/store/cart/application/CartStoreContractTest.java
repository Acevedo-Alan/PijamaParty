package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadCart} and {@link SaveCart} over the same storage. Owners are the customer and the guests
 * below, so a real adapter can act as each of them (Row Level Security, RNF-27).
 */
public abstract class CartStoreContractTest<T extends LoadCart & SaveCart> extends ContractTest<T> {

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

    private static void assertSameState(Optional<Cart> loaded, Cart expected) {
        assertThat(loaded).hasValueSatisfying(cart -> assertThat(cart).usingRecursiveComparison().isEqualTo(expected));
    }
}
