package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.application.CartView.ItemView;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
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

/** UC-09: {@link GetCart}, {@link ChangeItemQuantity}, {@link RemoveItem} and {@link AcknowledgePriceChanges}. */
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
    private static final Money NEW_PRICE = Money.of(1_600_000);

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryCoupons coupons = new InMemoryCoupons();
    private final InMemoryItemDescriptions descriptions = new InMemoryItemDescriptions();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GetCartService getCart = new GetCartService(carts, availability, coupons, descriptions);
    private final ChangeItemQuantityService changeQuantity =
        new ChangeItemQuantityService(carts, carts, availability, coupons, descriptions, clock);
    private final RemoveItemService removeItem =
        new RemoveItemService(carts, carts, availability, coupons, descriptions, clock);
    private final AcknowledgePriceChangesService acknowledgePrices =
        new AcknowledgePriceChangesService(carts, carts, availability, coupons, descriptions, clock);

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
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(cart(List.of(
            item(PIJAMA_ITEM, PIJAMA, 2), item(CAMISON_ITEM, CAMISON, 1))));
    }

    @Test
    void givenDescribedVariants_whenGettingTheCart_thenEachItemShowsItsVariantAlsoWhenItIsNoLongerSold() {
        var pijama = new ItemDescription("Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", Optional.of(
            new ItemDescription.Photo("data:image/webp;base64,UklGRg==",
                List.of(new ItemDescription.PhotoFile(400, "luna-400.webp")))));
        var camison = new ItemDescription("Camisón Sol", "camison-sol", "S", "Gris", "CS-S-GRIS", Optional.empty());
        descriptions.add(PIJAMA, pijama);
        descriptions.add(CAMISON, camison);
        availability.stopSelling(CAMISON);

        var items = getCart.execute(new GetCart.Query(GUEST)).items();

        assertThat(items).extracting(ItemView::description).containsExactly(Optional.of(pijama), Optional.of(camison));
        assertThat(items.get(1).status()).isEqualTo(LineStatus.UNAVAILABLE);
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

        CartView expected = cart(List.of(
            item(PIJAMA_ITEM, PIJAMA, 5), item(CAMISON_ITEM, CAMISON, 1)));
        assertThat(result).isEqualTo(new ChangeItemQuantity.Result.Changed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAnItemInTheCart_whenItsQuantityIsChangedTo0_thenItIsRemoved() {
        var result = changeQuantity.execute(new ChangeItemQuantity.Command(GUEST, PIJAMA_ITEM, 0));

        CartView expected = cart(List.of(item(CAMISON_ITEM, CAMISON, 1)));
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

        CartView expected = cart(List.of(item(PIJAMA_ITEM, PIJAMA, 2)));
        assertThat(result).isEqualTo(new RemoveItem.Result.Removed(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAVariantThatSoldOutWhileInTheCart_whenTheCartOpens_thenItIsShownAsUnavailableAndLeftOutOfTheTotal() {
        availability.sell(PIJAMA, PRICE, 0);

        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(new CartView(List.of(
                line(PIJAMA_ITEM, PIJAMA, 2, PRICE, Optional.empty(), LineStatus.UNAVAILABLE, 0),
                item(CAMISON_ITEM, CAMISON, 1)),
            Optional.empty(), Optional.empty(), PRICE, Money.zero(), PRICE));
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenTheCartOpens_thenItIsShownAsUnavailable() {
        availability.stopSelling(CAMISON);

        assertThat(getCart.execute(new GetCart.Query(GUEST)).items().get(1))
            .isEqualTo(line(CAMISON_ITEM, CAMISON, 1, PRICE, Optional.empty(), LineStatus.UNAVAILABLE, 0));
    }

    @Test
    void givenFewerUnitsLeftThanInTheCart_whenTheCartOpens_thenTheItemKeepsItsQuantityWithTheUnitsLeft() {
        availability.sell(PIJAMA, PRICE, 1);

        CartView cart = getCart.execute(new GetCart.Query(GUEST));

        assertThat(cart.items().get(0))
            .isEqualTo(line(PIJAMA_ITEM, PIJAMA, 2, PRICE, Optional.empty(), LineStatus.INSUFFICIENT_STOCK, 1));
        assertThat(cart.total()).isEqualTo(PRICE);
    }

    @Test
    void givenAPriceChangeInTheCatalog_whenTheCartOpens_thenTheTotalUsesTheNewPriceAndTheChangeIsAnnounced() {
        availability.sell(PIJAMA, NEW_PRICE, 5);

        CartView cart = getCart.execute(new GetCart.Query(GUEST));

        assertThat(cart.items().get(0)).isEqualTo(
            line(PIJAMA_ITEM, PIJAMA, 2, NEW_PRICE, Optional.of(PRICE), LineStatus.AVAILABLE, 5));
        assertThat(cart.total()).isEqualTo(Money.of(4_700_000));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(cart);
    }

    @Test
    void givenAPriceChange_whenTheCustomerAcknowledgesIt_thenItIsNoLongerAnnounced() {
        availability.sell(PIJAMA, NEW_PRICE, 5);

        var result = acknowledgePrices.execute(new AcknowledgePriceChanges.Command(GUEST));

        CartView expected = new CartView(List.of(
                line(PIJAMA_ITEM, PIJAMA, 2, NEW_PRICE, Optional.empty(), LineStatus.AVAILABLE, 5),
                item(CAMISON_ITEM, CAMISON, 1)),
            Optional.empty(), Optional.empty(), Money.of(4_700_000), Money.zero(), Money.of(4_700_000));
        assertThat(result).isEqualTo(new AcknowledgePriceChanges.Result.Acknowledged(expected));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(expected);
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenAcknowledgingPrices_thenItKeepsItsLastSeenPrice() {
        availability.stopSelling(PIJAMA);

        var result = acknowledgePrices.execute(new AcknowledgePriceChanges.Command(GUEST));

        assertThat(result).isInstanceOfSatisfying(AcknowledgePriceChanges.Result.Acknowledged.class,
            acknowledged -> assertThat(acknowledged.cart().items().get(0).unitPrice()).isEqualTo(PRICE));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().updatedAt()).isEqualTo(OPENED);
    }

    @Test
    void givenNoCartOrAnActorThatCannotOwnOne_whenAcknowledgingPrices_thenTheCartIsEmptyOrItIsForbidden() {
        assertThat(acknowledgePrices.execute(new AcknowledgePriceChanges.Command(OTHER_GUEST)))
            .isEqualTo(new AcknowledgePriceChanges.Result.Acknowledged(CartView.empty()));
        assertThat(acknowledgePrices.execute(new AcknowledgePriceChanges.Command(ADMIN)))
            .isEqualTo(new AcknowledgePriceChanges.Result.Forbidden());
        assertThat(carts.size()).isEqualTo(1);
    }

    @Test
    void givenAnItemOfAnotherCartOrNoCart_whenRemovingIt_thenItIsNotFound() {
        assertThat(removeItem.execute(new RemoveItem.Command(OTHER_GUEST, PIJAMA_ITEM)))
            .isEqualTo(new RemoveItem.Result.ItemNotFound());
        assertThat(removeItem.execute(new RemoveItem.Command(GUEST, CartItemId.of(UUID.randomUUID()))))
            .isEqualTo(new RemoveItem.Result.ItemNotFound());
        assertThat(getCart.execute(new GetCart.Query(GUEST)).items()).hasSize(2);
    }

    /** A line whose variant the catalog does not describe. */
    private static ItemView line(CartItemId id, VariantId variant, int quantity, Money unitPrice,
                                 Optional<Money> previousUnitPrice, LineStatus status, int available) {
        return new ItemView(id, variant, quantity, unitPrice, previousUnitPrice, status, available, Optional.empty());
    }

    /** An item in stock (5 available) at {@link #PRICE}, with no price change. */
    private static ItemView item(CartItemId id, VariantId variant, int quantity) {
        return new ItemView(id, variant, quantity, PRICE, Optional.empty(), LineStatus.AVAILABLE, 5, Optional.empty());
    }

    /** A cart of {@code items} in stock at {@link #PRICE}. */
    private static CartView cart(List<ItemView> items) {
        Money total = PRICE.times(items.stream().mapToInt(ItemView::quantity).sum());
        return new CartView(items, Optional.empty(), Optional.empty(), total, Money.zero(), total);
    }
}
