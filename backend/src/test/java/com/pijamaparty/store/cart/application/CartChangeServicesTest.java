package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.application.CartView.ItemView;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
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

/** UC-09: {@link GetCart}, {@link ChangeItemQuantity} and {@link RemoveItem}. */
class CartChangeServicesTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final Actor OTHER_GUEST = Actor.anonymous(Optional.of(new CartToken("another-token")),
        Optional.empty());
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final CartOwner GUEST_OWNER = new CartOwner.Guest(TOKEN.hash());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());
    private static final CartItemId PIJAMA_ITEM = CartItemId.of(UUID.randomUUID());
    private static final CartItemId CAMISON_ITEM = CartItemId.of(UUID.randomUUID());
    private static final Money PRICE = Money.of(1_500_000);

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GetCartService getCart = new GetCartService(carts);
    private final ChangeItemQuantityService changeQuantity =
        new ChangeItemQuantityService(carts, carts, availability, clock);
    private final RemoveItemService removeItem = new RemoveItemService(carts, carts, clock);

    /** The guest's cart holds 2 pijamas and 1 camisón; 5 of each are available. */
    @BeforeEach
    void fillCart() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST_OWNER, OPENED);
        cart.add(PIJAMA_ITEM, PIJAMA, 2, 5, PRICE, OPENED);
        cart.add(CAMISON_ITEM, CAMISON, 1, 5, PRICE, OPENED);
        carts.save(cart);
        availability.sell(PIJAMA, PRICE, 5);
        availability.sell(CAMISON, PRICE, 5);
    }

    @Test
    void givenACart_whenGettingIt_thenItHasItsItemsInTheOrderTheyWereAdded() {
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(new CartView(List.of(
            new ItemView(PIJAMA_ITEM, PIJAMA, 2, PRICE), new ItemView(CAMISON_ITEM, CAMISON, 1, PRICE))));
    }

    @Test
    void givenNoCartOrAnActorThatCannotOwnOne_whenGettingTheCart_thenItIsEmptyAndNoneIsCreated() {
        assertThat(getCart.execute(new GetCart.Query(OTHER_GUEST))).isEqualTo(CartView.empty());
        assertThat(getCart.execute(new GetCart.Query(ADMIN))).isEqualTo(CartView.empty());
        assertThat(getCart.execute(new GetCart.Query(Actor.anonymous(Optional.empty(), Optional.empty()))))
            .isEqualTo(CartView.empty());
        assertThat(carts.size()).isEqualTo(1);
    }

    @Test
    void givenAnItem_whenChangingItsQuantityWithinTheStock_thenTheFullCartShowsIt() {
        var result = changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 5));

        CartView expected = new CartView(List.of(
            new ItemView(PIJAMA_ITEM, PIJAMA, 5, PRICE), new ItemView(CAMISON_ITEM, CAMISON, 1, PRICE)));
        assertThat(result).isEqualTo(new ChangeItemQuantity.Result.Changed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnItemInTheCart_whenItsQuantityIsChangedTo0_thenItIsRemoved() {
        var result = changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 0));

        CartView expected = new CartView(List.of(new ItemView(CAMISON_ITEM, CAMISON, 1, PRICE)));
        assertThat(result).isEqualTo(new ChangeItemQuantity.Result.Changed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
    }

    @Test
    void givenAnItem_whenChangingItsQuantityAboveTheStock_thenItIsRejectedWithTheUnitsLeft() {
        availability.sell(PIJAMA, PRICE, 3);

        var result = changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 4));

        assertThat(result).isEqualTo(new ChangeItemQuantity.Result.InsufficientStock(PIJAMA, 3));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().item(PIJAMA_ITEM).orElseThrow().quantity())
            .isEqualTo(2);
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenChangingItsQuantity_thenOnlyRemovingItIsAllowed() {
        availability.stopSelling(PIJAMA);

        assertThat(changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 1)))
            .isEqualTo(new ChangeItemQuantity.Result.InsufficientStock(PIJAMA, 0));
        assertThat(changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 0)))
            .isInstanceOf(ChangeItemQuantity.Result.Changed.class);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().item(PIJAMA_ITEM)).isEmpty();
    }

    @Test
    void givenAnItemOfAnotherCartOrNoCart_whenChangingIt_thenItIsNotFound() {
        assertThat(changeQuantity.execute(new ChangeItemQuantity.Command(OTHER_GUEST, PIJAMA_ITEM, 1)))
            .isEqualTo(new ChangeItemQuantity.Result.ItemNotFound());
        assertThat(changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, CartItemId.of(UUID.randomUUID()), 1)))
            .isEqualTo(new ChangeItemQuantity.Result.ItemNotFound());
    }

    @Test
    void givenAnActorThatCannotOwnACart_whenChangingOrRemovingAnItem_thenItIsForbidden() {
        assertThat(changeQuantity.execute(new ChangeItemQuantity.Command(ADMIN, PIJAMA_ITEM, 1)))
            .isEqualTo(new ChangeItemQuantity.Result.Forbidden());
        assertThat(removeItem.execute(new RemoveItem.Command(ADMIN, PIJAMA_ITEM)))
            .isEqualTo(new RemoveItem.Result.Forbidden());
    }

    @Test
    void givenAnItem_whenRemovingIt_thenTheFullCartNoLongerHasIt() {
        var result = removeItem.execute(new RemoveItem.Command(GUEST, CAMISON_ITEM));

        CartView expected = new CartView(List.of(new ItemView(PIJAMA_ITEM, PIJAMA, 2, PRICE)));
        assertThat(result).isEqualTo(new RemoveItem.Result.Removed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnItemOfAnotherCartOrNoCart_whenRemovingIt_thenItIsNotFound() {
        assertThat(removeItem.execute(new RemoveItem.Command(OTHER_GUEST, PIJAMA_ITEM)))
            .isEqualTo(new RemoveItem.Result.ItemNotFound());
        assertThat(removeItem.execute(new RemoveItem.Command(GUEST, CartItemId.of(UUID.randomUUID()))))
            .isEqualTo(new RemoveItem.Result.ItemNotFound());
        assertThat(getCart.execute(new GetCart.Query(GUEST)).items()).hasSize(2);
    }
}
