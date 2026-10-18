package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.api.CartSummary;
import com.pijamaparty.store.cart.application.CartView.AppliedDiscountView;
import com.pijamaparty.store.cart.application.CartView.CouponView;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-23: automatic coupons in every cart view, priced on the cart's available lines. */
class AutomaticCouponServicesTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final CartToken TOKEN = new CartToken("a-guest-cart-token");
    private static final Actor GUEST = Actor.anonymous(Optional.of(TOKEN), Optional.empty());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId BATA = VariantId.of(UUID.randomUUID());
    private static final CartItemId PIJAMA_ITEM = CartItemId.of(UUID.randomUUID());
    private static final CartItemId BATA_ITEM = CartItemId.of(UUID.randomUUID());
    private static final UUID PIJAMAS = UUID.randomUUID();
    private static final UUID BATAS = UUID.randomUUID();

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final InMemoryVariantAvailability availability = new InMemoryVariantAvailability();
    private final InMemoryCoupons coupons = new InMemoryCoupons();
    private final InMemoryItemDescriptions descriptions = new InMemoryItemDescriptions();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GetCartService getCart = new GetCartService(carts, availability, coupons, descriptions);
    private final RemoveItemService removeItem =
        new RemoveItemService(carts, carts, availability, coupons, descriptions, clock);
    private final ApplyCouponService applyCoupon =
        new ApplyCouponService(carts, carts, availability, coupons, descriptions, clock);
    private final CartSummariesService summaries = new CartSummariesService(carts, availability, coupons);

    /** The guest's cart: 1 pijama at ARS 25,000 (category Pijamas) and 1 bata at ARS 10,000 (category Batas). */
    @BeforeEach
    void fillCart() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Guest(TOKEN.hash()), OPENED);
        cart.add(PIJAMA_ITEM, PIJAMA, 1, 5, Money.of(2_500_000), OPENED);
        cart.add(BATA_ITEM, BATA, 1, 5, Money.of(1_000_000), OPENED);
        carts.save(cart);
        availability.sell(PIJAMA, UUID.randomUUID(), PIJAMAS, Money.of(2_500_000), 5);
        availability.sell(BATA, UUID.randomUUID(), BATAS, Money.of(1_000_000), 5);
    }

    @Test
    void givenARule10PercentFrom30000_whenTheCartExceeds30000_thenTheDiscountAppearsWithoutEnteringACode() {
        coupons.automatic("VERANO10", 10, Money.of(3_000_000));

        CartView cart = getCart.execute(new GetCart.Query(GUEST));

        assertThat(cart.coupon()).isEmpty();
        assertThat(cart.appliedDiscount()).contains(new AppliedDiscountView("VERANO10", true));
        assertThat(cart.discount()).isEqualTo(Money.of(350_000));
        assertThat(cart.total()).isEqualTo(Money.of(3_150_000));
        assertThat(summaries.of(GUEST))
            .contains(new CartSummary(Money.of(3_500_000), Money.of(350_000), Money.of(3_150_000)));
    }

    @Test
    void givenACartThatFallsBelowTheMinimumWhenAnItemIsRemoved_whenPricedAgain_thenTheDiscountIsWithdrawn() {
        coupons.automatic("VERANO10", 10, Money.of(3_000_000));

        var result = removeItem.execute(new RemoveItem.Command(GUEST, BATA_ITEM));

        CartView cart = ((RemoveItem.Result.Removed) result).cart();
        assertThat(cart.appliedDiscount()).isEmpty();
        assertThat(cart.discount()).isEqualTo(Money.zero());
        assertThat(cart.total()).isEqualTo(Money.of(2_500_000));
    }

    @Test
    void givenAnItemThatIsNotAvailable_whenTheCartIsPriced_thenItDoesNotCountTowardsTheMinimum() {
        coupons.automatic("VERANO10", 10, Money.of(3_000_000));
        availability.sell(BATA, UUID.randomUUID(), BATAS, Money.of(1_000_000), 0);

        assertThat(getCart.execute(new GetCart.Query(GUEST)).appliedDiscount()).isEmpty();
    }

    @Test
    void givenAnAutomaticCouponOfACategory_whenTheCartHoldsItemsOfItAndOthers_thenOnlyItsItemsAreDiscounted() {
        coupons.automatic("BATAS20", 20, Money.of(1), Set.of(BATAS));

        CartView cart = getCart.execute(new GetCart.Query(GUEST));

        assertThat(cart.appliedDiscount()).contains(new AppliedDiscountView("BATAS20", true));
        assertThat(cart.discount()).isEqualTo(Money.of(200_000));
    }

    @Test
    void givenAnAutomaticCouponThatDiscountsMore_whenACodeIsApplied_thenTheCodeIsHeldButTheAutomaticOneDiscounts() {
        coupons.automatic("VERANO20", 20, Money.of(1));
        coupons.percentage("PIJAMA10", 10);

        var result = applyCoupon.execute(new ApplyCoupon.Command(GUEST, "PIJAMA10"));

        CartView cart = ((ApplyCoupon.Result.Applied) result).cart();
        assertThat(cart.coupon()).contains(new CouponView("PIJAMA10", CouponCheck.Status.APPLICABLE, Money.zero()));
        assertThat(cart.appliedDiscount()).contains(new AppliedDiscountView("VERANO20", true));
        assertThat(cart.discount()).isEqualTo(Money.of(700_000));
        assertThat(getCart.execute(new GetCart.Query(GUEST))).isEqualTo(cart);
    }

    @Test
    void givenATypedCodeThatDiscountsMoreThanAnAutomaticCoupon_whenApplied_thenOnlyTheCodesDiscountApplies() {
        coupons.automatic("VERANO10", 10, Money.of(1));
        coupons.percentage("PIJAMA20", 20);

        var result = applyCoupon.execute(new ApplyCoupon.Command(GUEST, "PIJAMA20"));

        CartView cart = ((ApplyCoupon.Result.Applied) result).cart();
        assertThat(cart.appliedDiscount()).contains(new AppliedDiscountView("PIJAMA20", false));
        assertThat(cart.discount()).isEqualTo(Money.of(700_000));
    }

    @Test
    void givenAnAutomaticCoupon_whenItsCodeIsTyped_thenItIsUnknown() {
        coupons.automatic("VERANO10", 10, Money.of(1));

        assertThat(applyCoupon.execute(new ApplyCoupon.Command(GUEST, "VERANO10")))
            .isEqualTo(new ApplyCoupon.Result.Unknown());
    }
}
