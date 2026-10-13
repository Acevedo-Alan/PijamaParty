package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.cart.application.AddItemToCart.Command;
import com.pijamaparty.store.cart.application.AddItemToCart.Result;
import com.pijamaparty.store.cart.application.CartView.ItemView;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.PricedCart.LineStatus;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
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
import org.junit.jupiter.api.Test;

class AddItemToCartServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final CartOwner GUEST_OWNER = new CartOwner.Guest(TOKEN.hash());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final Money PRICE = Money.of(1_500_000);
    private static final ItemDescription PIJAMA_DESCRIPTION =
        new ItemDescription("Pijama Luna", "pijama-luna", "M", "Rosa", "PL-M-ROSA", Optional.empty());

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryItemDescriptions descriptions = new InMemoryItemDescriptions();
    private final AddItemToCartService service = new AddItemToCartService(carts, carts, availability,
        new InMemoryCoupons(), descriptions, new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAVisitorWithoutACart_whenAddingTheFirstProduct_thenACartTiedToTheirBrowserIsCreated() {
        availability.sell(PIJAMA, PRICE, 5);
        descriptions.add(PIJAMA, PIJAMA_DESCRIPTION);

        Result result = service.execute(new Command(GUEST, PIJAMA, 2));

        CartItemId itemId = CartItemId.of(InMemoryIdGenerator.nth(2));
        ItemView item = new ItemView(itemId, PIJAMA, 2, PRICE, Optional.empty(), LineStatus.AVAILABLE, 5,
            Optional.of(PIJAMA_DESCRIPTION));
        assertThat(result).isEqualTo(new Result.Added(
            new CartView(List.of(item), Optional.empty(), Money.of(3_000_000), Money.zero(), Money.of(3_000_000))));
        assertThat(carts.findByOwner(GUEST_OWNER)).hasValueSatisfying(cart -> {
            assertThat(cart.id()).isEqualTo(CartId.of(InMemoryIdGenerator.nth(1)));
            assertThat(cart.createdAt()).isEqualTo(NOW);
            assertThat(cart.items()).containsExactly(new CartItem(itemId, PIJAMA, 2, PRICE, NOW));
        });
    }

    @Test
    void givenAVariantWithStock3And2UnitsInTheCart_whenAdding2More_thenItIsRejectedWithSoloQuedan3() {
        availability.sell(PIJAMA, PRICE, 3);
        service.execute(new Command(GUEST, PIJAMA, 2));

        Result result = service.execute(new Command(GUEST, PIJAMA, 2));

        assertThat(result).isEqualTo(new Result.InsufficientStock(PIJAMA, 3));
        assertThat(carts.findByOwner(GUEST_OWNER).orElseThrow().items())
            .singleElement().satisfies(item -> assertThat(item.quantity()).isEqualTo(2));
    }

    @Test
    void givenAVariantInTheCart_whenAddingItAgain_thenItsLineGrowsWithTheCurrentPrice() {
        availability.sell(PIJAMA, PRICE, 5);
        service.execute(new Command(GUEST, PIJAMA, 2));
        availability.sell(PIJAMA, Money.of(1_400_000), 5);

        Result result = service.execute(new Command(GUEST, PIJAMA, 3));

        assertThat(result).isEqualTo(new Result.Added(new CartView(List.of(
            new ItemView(CartItemId.of(InMemoryIdGenerator.nth(2)), PIJAMA, 5, Money.of(1_400_000), Optional.empty(),
                LineStatus.AVAILABLE, 5, Optional.empty())),
            Optional.empty(), Money.of(7_000_000), Money.zero(), Money.of(7_000_000))));
        assertThat(carts.size()).isEqualTo(1);
    }

    @Test
    void givenAVariantTheCatalogDoesNotSell_whenAddingIt_thenItIsUnavailableAndNoCartIsCreated() {
        Result result = service.execute(new Command(GUEST, PIJAMA, 1));

        assertThat(result).isEqualTo(new Result.VariantUnavailable(PIJAMA));
        assertThat(carts.size()).isZero();
    }

    @Test
    void givenAVariantWithoutStock_whenAddingIt_thenItIsRejectedAndNoCartIsCreated() {
        availability.sell(PIJAMA, PRICE, 0);

        assertThat(service.execute(new Command(GUEST, PIJAMA, 1))).isEqualTo(new Result.InsufficientStock(PIJAMA, 0));
        assertThat(carts.size()).isZero();
    }

    @Test
    void givenACustomer_whenAddingAProduct_thenTheCartIsTheirs() {
        UserId customer = UserId.of(UUID.randomUUID());
        availability.sell(PIJAMA, PRICE, 5);

        service.execute(new Command(Actor.customer(customer, Optional.of(TOKEN), Optional.empty()), PIJAMA, 1));

        assertThat(carts.findByOwner(new CartOwner.Customer(customer))).isPresent();
        assertThat(carts.findByOwner(GUEST_OWNER)).isEmpty();
    }

    @Test
    void givenAnActorThatCannotOwnACart_whenAddingAProduct_thenItIsForbidden() {
        availability.sell(PIJAMA, PRICE, 5);

        assertThat(service.execute(new Command(Actor.admin(UserId.of(UUID.randomUUID())), PIJAMA, 1)))
            .isEqualTo(new Result.Forbidden());
        assertThat(service.execute(new Command(Actor.anonymous(Optional.empty(), Optional.empty()), PIJAMA, 1)))
            .isEqualTo(new Result.Forbidden());
        assertThat(carts.size()).isZero();
    }

    @Test
    void givenAQuantityOutOfRange_whenMakingTheCommand_thenItFails() {
        assertThatThrownBy(() -> new Command(GUEST, PIJAMA, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(GUEST, PIJAMA, Cart.MAX_QUANTITY + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
