package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.cart.application.MergeGuestCart.Command;
import com.pijamaparty.store.cart.application.MergeGuestCart.Result;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItem;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-19 (T-E4-04): the guest cart joins the customer's cart at sign-in. */
class MergeGuestCartServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final UserId CUSTOMER_ID = UserId.of(UUID.randomUUID());
    private static final Actor SIGNING_IN = Actor.customer(CUSTOMER_ID, Optional.of(TOKEN), Optional.empty());
    private static final CartOwner GUEST = new CartOwner.Guest(TOKEN.hash());
    private static final CartOwner CUSTOMER = new CartOwner.Customer(CUSTOMER_ID);
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());
    private static final Money PRICE = Money.of(1_500_000);

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final MergeGuestCartService service = new MergeGuestCartService(carts, carts, carts, availability,
        new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAGuestCartAndACustomerCart_whenSigningIn_thenTheItemsJoinWithoutExceedingTheStockAndTheGuestCartIsGone() {
        availability.sell(PIJAMA, PRICE, 4);
        availability.sell(CAMISON, PRICE, 10);
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        own.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 3, 4, PRICE, OPENED);
        carts.save(own);
        Cart guest = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        guest.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 4, PRICE, OPENED.plusSeconds(60));
        guest.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 10, PRICE, OPENED.plusSeconds(60));
        carts.save(guest);

        assertThat(service.execute(new Command(SIGNING_IN))).isEqualTo(new Result.Merged());

        assertThat(carts.findByOwner(GUEST)).isEmpty();
        Cart merged = carts.findByOwner(CUSTOMER).orElseThrow();
        assertThat(merged.id()).isEqualTo(own.id());
        assertThat(merged.items()).extracting(CartItem::variantId, CartItem::quantity)
            .containsExactly(tuple(PIJAMA, 4), tuple(CAMISON, 1));
        assertThat(merged.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenAGuestCartAndNoCustomerCart_whenSigningIn_thenTheCustomerGetsANewCartWithTheGuestsLinesAndCoupon() {
        availability.sell(PIJAMA, PRICE, 4);
        Cart guest = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        CartItemId line = CartItemId.of(UUID.randomUUID());
        guest.add(line, PIJAMA, 2, 4, PRICE, OPENED);
        CouponId coupon = CouponId.of(UUID.randomUUID());
        guest.applyCoupon(coupon, OPENED);
        carts.save(guest);

        service.execute(new Command(SIGNING_IN));

        assertThat(carts.size()).isOne();
        Cart cart = carts.findByOwner(CUSTOMER).orElseThrow();
        assertThat(cart.id()).isEqualTo(CartId.of(InMemoryIdGenerator.nth(1)));
        assertThat(cart.items()).containsExactly(new CartItem(line, PIJAMA, 2, PRICE, OPENED));
        assertThat(cart.coupon()).contains(coupon);
    }

    @Test
    void givenAnEmptyGuestCartAndNoCustomerCart_whenSigningIn_thenTheGuestCartIsGoneAndNoCartIsCreated() {
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED));

        assertThat(service.execute(new Command(SIGNING_IN))).isEqualTo(new Result.Merged());

        assertThat(carts.size()).isZero();
    }

    @Test
    void givenNoGuestCartForTheToken_whenSigningIn_thenTheCustomersCartIsUntouched() {
        Cart own = Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, OPENED);
        carts.save(own);

        assertThat(service.execute(new Command(SIGNING_IN))).isEqualTo(new Result.NoGuestCart());

        assertThat(carts.findByOwner(CUSTOMER)).hasValueSatisfying(cart ->
            assertThat(cart.updatedAt()).isEqualTo(OPENED));
    }

    @Test
    void givenASignInOfTheSameBrowserThatDeletedTheGuestCartFirst_whenThisOneMerges_thenTheLinesDoNotJoinTwice() {
        availability.sell(PIJAMA, PRICE, 10);
        Cart guest = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
        guest.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 10, PRICE, OPENED);
        carts.save(guest);
        DeleteCart deletedByTheOther = cartId -> false;
        var concurrent = new MergeGuestCartService(carts, carts, deletedByTheOther, availability,
            new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(concurrent.execute(new Command(SIGNING_IN))).isEqualTo(new Result.NoGuestCart());

        assertThat(carts.findByOwner(CUSTOMER)).isEmpty();
    }

    @Test
    void givenAnActorThatIsNotACustomerWithACartToken_whenMerging_thenNothingHappens() {
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED));

        assertThat(service.execute(new Command(Actor.customer(CUSTOMER_ID, Optional.empty(), Optional.empty()))))
            .isEqualTo(new Result.NoGuestCart());
        assertThat(service.execute(new Command(Actor.anonymous(Optional.of(TOKEN), Optional.empty()))))
            .isEqualTo(new Result.NoGuestCart());
        assertThat(service.execute(new Command(Actor.admin(UserId.of(UUID.randomUUID())))))
            .isEqualTo(new Result.NoGuestCart());
        assertThat(carts.findByOwner(GUEST)).isPresent();
    }
}
