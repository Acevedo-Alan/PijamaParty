package com.pijamaparty.store.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
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
    void givenACart_whenCopied_thenChangingTheCopyLeavesTheOriginalAlone() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, PRICE, OPENED);

        Cart copy = cart.copy();
        copy.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, PRICE, LATER);

        assertThat(cart.items()).hasSize(1);
        assertThat(copy.items()).hasSize(2);
    }

    @Test
    void givenTwoLinesOfOneVariant_whenRebuildingTheCart_thenItFails() {
        var first = new CartItem(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, PRICE, OPENED);
        var second = new CartItem(CartItemId.of(UUID.randomUUID()), PIJAMA, 1, PRICE, OPENED);

        assertThatThrownBy(() -> Cart.of(CartId.of(UUID.randomUUID()), GUEST, List.of(first, second), OPENED, OPENED))
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

    @Test
    void givenSomethingThatIsNotATokenHash_whenMakingAGuestOwner_thenItFails() {
        assertThatThrownBy(() -> new CartOwner.Guest("a-guest-cart-token")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartOwner.Guest(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
