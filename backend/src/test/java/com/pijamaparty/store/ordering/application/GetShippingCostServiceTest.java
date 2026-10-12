package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-12, UC-13: the shipping cost and the summary of step 1 of the checkout, over in-memory fakes. */
class GetShippingCostServiceTest {

    private static final Actor GUEST = Actor.anonymous(Optional.of(new CartToken("a-guest-cart-token")),
        Optional.empty());

    private final InMemoryCartAmounts carts = new InMemoryCartAmounts();
    private final InMemoryShippingQuotes shipping = new InMemoryShippingQuotes();
    private final GetShippingCostService getShippingCost = new GetShippingCostService(carts, shipping);

    /** Santa Fe ARS 4,500 and Córdoba ARS 6,500; Mendoza has no shipping. */
    @BeforeEach
    void setRates() {
        shipping.rate(Province.SANTA_FE, Money.of(450_000));
        shipping.rate(Province.CORDOBA, Money.of(650_000));
    }

    @Test
    void givenACartWithADiscount_whenAskingTheShipping_thenTheTotalAddsItToTheSubtotalAfterTheDiscount() {
        carts.put(GUEST, new CartAmounts.Amounts(Money.of(4_000_000), Money.of(400_000)));

        assertThat(execute(Province.SANTA_FE)).isEqualTo(new GetShippingCost.Result.Quoted(Province.SANTA_FE,
            Money.of(4_000_000), Money.of(400_000), Money.of(450_000), Money.of(4_050_000)));
    }

    @Test
    void givenABuyerWhoChangesTheProvince_whenAskingAgain_thenTheShippingAndTheTotalAreRecalculated() {
        carts.put(GUEST, new CartAmounts.Amounts(Money.of(4_000_000), Money.zero()));

        assertThat(execute(Province.SANTA_FE)).isEqualTo(new GetShippingCost.Result.Quoted(Province.SANTA_FE,
            Money.of(4_000_000), Money.zero(), Money.of(450_000), Money.of(4_450_000)));
        assertThat(execute(Province.CORDOBA)).isEqualTo(new GetShippingCost.Result.Quoted(Province.CORDOBA,
            Money.of(4_000_000), Money.zero(), Money.of(650_000), Money.of(4_650_000)));
    }

    @Test
    void givenFreeShippingFrom50000_whenTheSubtotalAfterTheDiscountIs50000OrMore_thenShippingCosts0() {
        shipping.freeShippingFrom(Money.of(5_000_000));
        carts.put(GUEST, new CartAmounts.Amounts(Money.of(5_500_000), Money.of(500_000)));

        assertThat(execute(Province.SANTA_FE)).isEqualTo(new GetShippingCost.Result.Quoted(Province.SANTA_FE,
            Money.of(5_500_000), Money.of(500_000), Money.zero(), Money.of(5_000_000)));
    }

    @Test
    void givenFreeShippingFrom50000_whenOnlyTheSubtotalBeforeTheDiscountReachesIt_thenShippingIsCharged() {
        shipping.freeShippingFrom(Money.of(5_000_000));
        carts.put(GUEST, new CartAmounts.Amounts(Money.of(5_000_000), Money.of(500_000)));

        assertThat(execute(Province.SANTA_FE)).isEqualTo(new GetShippingCost.Result.Quoted(Province.SANTA_FE,
            Money.of(5_000_000), Money.of(500_000), Money.of(450_000), Money.of(4_950_000)));
    }

    @Test
    void givenAProvinceWithoutACost_whenTheBuyerPicksIt_thenThereIsNoShippingToThatZone() {
        carts.put(GUEST, new CartAmounts.Amounts(Money.of(4_000_000), Money.zero()));

        assertThat(execute(Province.MENDOZA)).isEqualTo(new GetShippingCost.Result.NoShipping(Province.MENDOZA));
    }

    @Test
    void givenNoCartToSell_whenAskingTheShipping_thenTheCartIsEmpty() {
        assertThat(execute(Province.SANTA_FE)).isEqualTo(new GetShippingCost.Result.EmptyCart());
    }

    private GetShippingCost.Result execute(Province province) {
        return getShippingCost.execute(new GetShippingCost.Query(GUEST, province));
    }
}
