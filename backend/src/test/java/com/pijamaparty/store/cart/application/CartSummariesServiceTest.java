package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.api.CartSummary;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-13: the cart the checkout sells, priced as every cart response. */
class CartSummariesServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryCoupons coupons = new InMemoryCoupons();
    private final CartSummariesService summaries = new CartSummariesService(carts, availability, coupons);
    private Cart cart;

    /** The guest's cart: 2 pijamas at ARS 20,000 and 1 camisón at ARS 10,000, all available. */
    @BeforeEach
    void fillCart() {
        cart = Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Guest(TOKEN.hash()), OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, Money.of(2_000_000), OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 1, 5, Money.of(1_000_000), OPENED);
        carts.save(cart);
        availability.sell(PIJAMA, Money.of(2_000_000), 5);
        availability.sell(CAMISON, Money.of(1_000_000), 5);
    }

    @Test
    void givenACartWithAnApplicableCoupon_whenSummarized_thenItHasTheSubtotalTheDiscountAndTheTotal() {
        cart.applyCoupon(coupons.percentage("VERANO10", 10), OPENED);
        carts.save(cart);

        assertThat(summaries.of(GUEST))
            .contains(new CartSummary(Money.of(5_000_000), Money.of(500_000), Money.of(4_500_000)));
    }

    @Test
    void givenAnUnavailableLine_whenSummarized_thenOnlyTheAvailableOnesCount() {
        availability.stopSelling(CAMISON);

        assertThat(summaries.of(GUEST)).contains(new CartSummary(Money.of(4_000_000), Money.zero(),
            Money.of(4_000_000)));
    }

    @Test
    void givenNoAvailableLine_whenSummarized_thenThereIsNothingToSell() {
        availability.stopSelling(PIJAMA);
        availability.stopSelling(CAMISON);

        assertThat(summaries.of(GUEST)).isEmpty();
    }

    @Test
    void givenAnActorWithoutACartOrThatCannotOwnOne_whenSummarized_thenThereIsNothingToSell() {
        assertThat(summaries.of(Actor.anonymous(Optional.of(new CartToken("another-token")), Optional.empty())))
            .isEmpty();
        assertThat(summaries.of(Actor.anonymous(Optional.empty(), Optional.empty()))).isEmpty();
        assertThat(summaries.of(Actor.admin(UserId.of(UUID.randomUUID())))).isEmpty();
        assertThat(summaries.of(Actor.system())).isEmpty();
    }
}
