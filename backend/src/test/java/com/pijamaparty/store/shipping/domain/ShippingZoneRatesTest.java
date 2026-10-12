package com.pijamaparty.store.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC-12: the rules of the shipping costs per province. */
class ShippingZoneRatesTest {

    @Test
    void givenAFreeAndAPaidCost_whenParsed_thenEachProvinceHasItsCostAndTheOthersHaveNoShipping() {
        ShippingZoneRates.Parsed parsed = ShippingZoneRates.parse(List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, 0),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 1_000_000_000L)));

        assertThat(parsed).isInstanceOfSatisfying(ShippingZoneRates.Parsed.Valid.class, valid -> {
            assertThat(valid.rates().costOf(Province.SANTA_FE)).contains(Money.zero());
            assertThat(valid.rates().costOf(Province.CORDOBA)).contains(Money.of(1_000_000_000L));
            assertThat(valid.rates().costOf(Province.MENDOZA)).isEmpty();
        });
    }

    @Test
    void givenCostsOutOfRangeOrARepeatedProvince_whenParsed_thenEachOfThoseProvincesIsRefused() {
        ShippingZoneRates.Parsed parsed = ShippingZoneRates.parse(List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, -1),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 1_000_000_001L),
            new ShippingZoneRates.TypedRate(Province.SALTA, 500_000),
            new ShippingZoneRates.TypedRate(Province.SALTA, 600_000),
            new ShippingZoneRates.TypedRate(Province.JUJUY, 500_000)));

        assertThat(parsed).isEqualTo(new ShippingZoneRates.Parsed.Invalid(
            Set.of(Province.SANTA_FE, Province.CORDOBA, Province.SALTA)));
    }

    @Test
    void givenNoCosts_whenParsed_thenNoProvinceHasShipping() {
        assertThat(ShippingZoneRates.parse(List.of()))
            .isEqualTo(new ShippingZoneRates.Parsed.Valid(ShippingZoneRates.none()));
    }

    @Test
    void givenACostAboveTheMaximum_whenBuilt_thenItIsRejected() {
        assertThatThrownBy(() -> ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(1_000_000_001L))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenCostsInAnyOrder_whenListed_thenTheyFollowTheProvinceOrder() {
        ShippingZoneRates rates = ShippingZoneRates.of(Map.of(Province.TUCUMAN, Money.of(1),
            Province.BUENOS_AIRES, Money.of(2)));

        assertThat(rates.costs().keySet()).containsExactly(Province.BUENOS_AIRES, Province.TUCUMAN);
    }
}
