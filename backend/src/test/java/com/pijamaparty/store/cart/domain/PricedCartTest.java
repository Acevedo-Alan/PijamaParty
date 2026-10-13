package com.pijamaparty.store.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.cart.domain.PricedCart.LineStatus;
import com.pijamaparty.store.cart.domain.PricedCart.PricedLine;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-09, UC-10: a cart priced with the current catalog prices and stock, and its coupon discount. */
class PricedCartTest {

    private static final Instant OPENED = Instant.parse("2026-10-10T12:00:00Z");
    private static final CartOwner GUEST = new CartOwner.Guest(new CartToken("a-guest-cart-token").hash());
    private static final VariantId PIJAMA = VariantId.of(UUID.randomUUID());
    private static final VariantId CAMISON = VariantId.of(UUID.randomUUID());
    private static final Money PIJAMA_PRICE = Money.of(1_500_000);
    private static final Money CAMISON_PRICE = Money.of(990_000);

    private final Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED);
    private CartItem pijama;
    private CartItem camison;

    /** 2 pijamas at ARS 15,000 and 3 camisones at ARS 9,900. */
    @BeforeEach
    void fillCart() {
        cart.add(CartItemId.of(UUID.randomUUID()), PIJAMA, 2, 5, PIJAMA_PRICE, OPENED);
        cart.add(CartItemId.of(UUID.randomUUID()), CAMISON, 3, 5, CAMISON_PRICE, OPENED);
        pijama = cart.items().get(0);
        camison = cart.items().get(1);
    }

    @Test
    void givenEveryItemInStockAtItsPrice_whenPricing_thenTheTotalIsTheSumOfTheLinesAndNothingIsAnnounced() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(PIJAMA_PRICE, 5),
            CAMISON, new SellableVariant(CAMISON_PRICE, 3)));

        assertThat(priced.lines()).containsExactly(
            new PricedLine(pijama, PIJAMA_PRICE, Optional.empty(), LineStatus.AVAILABLE, 5),
            new PricedLine(camison, CAMISON_PRICE, Optional.empty(), LineStatus.AVAILABLE, 3));
        assertThat(priced.subtotal()).isEqualTo(Money.of(5_970_000));
        assertThat(priced.discount()).isEqualTo(Money.zero());
        assertThat(priced.total()).isEqualTo(Money.of(5_970_000));
    }

    @Test
    void givenAPriceChangeInTheCatalog_whenPricing_thenTheTotalUsesTheNewPriceAndTheChangeIsAnnounced() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(Money.of(1_600_000), 5),
            CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        assertThat(priced.lines().get(0)).isEqualTo(new PricedLine(pijama, Money.of(1_600_000),
            Optional.of(PIJAMA_PRICE), LineStatus.AVAILABLE, 5));
        assertThat(priced.lines().get(1).previousUnitPrice()).isEmpty();
        assertThat(priced.total()).isEqualTo(Money.of(6_170_000));
    }

    @Test
    void givenAVariantThatSoldOutWhileInTheCart_whenPricing_thenItIsUnavailableAndLeftOutOfTheTotal() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(PIJAMA_PRICE, 0),
            CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        assertThat(priced.lines().get(0)).isEqualTo(new PricedLine(pijama, PIJAMA_PRICE, Optional.empty(),
            LineStatus.UNAVAILABLE, 0));
        assertThat(priced.total()).isEqualTo(Money.of(2_970_000));
    }

    @Test
    void givenAVariantTheCatalogNoLongerSells_whenPricing_thenItIsUnavailableAtItsLastSeenPrice() {
        PricedCart priced = PricedCart.of(cart, Map.of(CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        assertThat(priced.lines().get(0)).isEqualTo(new PricedLine(pijama, PIJAMA_PRICE, Optional.empty(),
            LineStatus.UNAVAILABLE, 0));
        assertThat(priced.subtotal()).isEqualTo(Money.of(2_970_000));
    }

    @Test
    void givenFewerUnitsLeftThanInTheCart_whenPricing_thenTheLineKeepsItsQuantityWithTheUnitsLeftAndIsLeftOutOfTheTotal() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(PIJAMA_PRICE, 1),
            CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        assertThat(priced.lines().get(0)).isEqualTo(new PricedLine(pijama, PIJAMA_PRICE, Optional.empty(),
            LineStatus.INSUFFICIENT_STOCK, 1));
        assertThat(priced.lines().get(0).item().quantity()).isEqualTo(2);
        assertThat(priced.total()).isEqualTo(Money.of(2_970_000));
    }

    @Test
    void givenASoldOutVariantWhosePriceChanged_whenPricing_thenTheChangeIsStillAnnounced() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(Money.of(1_400_000), 0),
            CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        assertThat(priced.lines().get(0)).isEqualTo(new PricedLine(pijama, Money.of(1_400_000),
            Optional.of(PIJAMA_PRICE), LineStatus.UNAVAILABLE, 0));
    }

    @Test
    void givenAnEmptyCart_whenPricing_thenEveryTotalIsZero() {
        PricedCart priced = PricedCart.of(Cart.open(CartId.of(UUID.randomUUID()), GUEST, OPENED), Map.of());

        assertThat(priced).isEqualTo(PricedCart.empty());
    }

    @Test
    void givenAPricedCart_whenACouponDiscountIsTakenOff_thenTheTotalIsWhatIsLeftOfTheSubtotal() {
        PricedCart priced = PricedCart.of(cart, Map.of(
            PIJAMA, new SellableVariant(PIJAMA_PRICE, 5),
            CAMISON, new SellableVariant(CAMISON_PRICE, 5)));

        PricedCart discounted = priced.withDiscount(Money.of(597_000));

        assertThat(discounted.subtotal()).isEqualTo(Money.of(5_970_000));
        assertThat(discounted.discount()).isEqualTo(Money.of(597_000));
        assertThat(discounted.total()).isEqualTo(Money.of(5_373_000));
        assertThat(discounted.lines()).isEqualTo(priced.lines());
        assertThat(priced.withDiscount(priced.subtotal()).total()).isEqualTo(Money.zero());
    }

    @Test
    void givenAPricedCart_whenADiscountAboveTheSubtotalIsTakenOff_thenItFails() {
        PricedCart priced = PricedCart.of(cart, Map.of(PIJAMA, new SellableVariant(PIJAMA_PRICE, 5)));

        assertThatThrownBy(() -> priced.withDiscount(priced.subtotal().plus(Money.of(1))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTheLineStatuses_whenPricing_thenTheCartHasSomethingToSellOnlyWithAnAvailableLine() {
        PricedCart priced = PricedCart.of(cart, Map.of(PIJAMA, new SellableVariant(PIJAMA_PRICE, 1)));

        assertThat(priced.lines()).extracting(PricedLine::status)
            .containsExactly(LineStatus.INSUFFICIENT_STOCK, LineStatus.UNAVAILABLE);
        assertThat(priced.hasAvailableLines()).isFalse();
        assertThat(PricedCart.of(cart, Map.of(CAMISON, new SellableVariant(CAMISON_PRICE, 3))).hasAvailableLines())
            .isTrue();
        assertThat(PricedCart.empty().hasAvailableLines()).isFalse();
    }

    @Test
    void givenEveryLineAvailableAtThePriceSeen_whenCheckingOut_thenTheCartIsReady() {
        var both = Map.of(PIJAMA, new SellableVariant(PIJAMA_PRICE, 5), CAMISON, new SellableVariant(CAMISON_PRICE, 3));

        assertThat(PricedCart.of(cart, both).isReadyForCheckout()).isTrue();
    }

    @Test
    void givenALineNotAvailableOrWithAPriceChange_whenCheckingOut_thenTheCartIsNotReady() {
        var camison = new SellableVariant(CAMISON_PRICE, 3);

        assertThat(PricedCart.of(cart, Map.of(PIJAMA, new SellableVariant(PIJAMA_PRICE, 1), CAMISON, camison))
            .isReadyForCheckout()).isFalse();
        assertThat(PricedCart.of(cart, Map.of(CAMISON, camison)).isReadyForCheckout()).isFalse();
        assertThat(PricedCart.of(cart, Map.of(PIJAMA, new SellableVariant(Money.of(1_400_000), 5), CAMISON, camison))
            .isReadyForCheckout()).isFalse();
        assertThat(PricedCart.empty().isReadyForCheckout()).isFalse();
    }
}
