package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.ShippingQuote;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-12, UC-13: the shipping cost the checkout charges, over the zone table of the in-memory store. */
class ShippingCostsServiceTest {

    private static final Instant AT = Instant.parse("2026-10-12T15:00:00Z");
    private static final com.pijamaparty.store.shipping.api.Province SANTA_FE =
        com.pijamaparty.store.shipping.api.Province.SANTA_FE;
    private static final com.pijamaparty.store.shipping.api.Province CORDOBA =
        com.pijamaparty.store.shipping.api.Province.CORDOBA;
    private static final com.pijamaparty.store.shipping.api.Province MENDOZA =
        com.pijamaparty.store.shipping.api.Province.MENDOZA;

    private final InMemoryShippingStore store = new InMemoryShippingStore();
    private final ShippingCostsService costs =
        new ShippingCostsService(new ZoneTableShippingCostCalculator(store), store);

    /** Santa Fe ARS 4,500, Córdoba free; Mendoza has no shipping. */
    @BeforeEach
    void setRates() {
        store.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(450_000),
            Province.CORDOBA, Money.zero())), AT);
    }

    @Test
    void givenAProvinceWithACost_whenQuoted_thenItCostsWhatTheAdministratorSet() {
        assertThat(costs.quote(SANTA_FE, Money.of(2_000_000))).isEqualTo(new ShippingQuote.Cost(Money.of(450_000)));
        assertThat(costs.quote(CORDOBA, Money.of(2_000_000))).isEqualTo(new ShippingQuote.Cost(Money.zero()));
    }

    @Test
    void givenAProvinceWithoutACost_whenQuoted_thenThereIsNoShippingAtAnySubtotal() {
        store.saveSettings(new ShippingSettings(Optional.of(Money.of(5_000_000))), AT);

        assertThat(costs.quote(MENDOZA, Money.of(2_000_000))).isEqualTo(new ShippingQuote.NoShipping());
        assertThat(costs.quote(MENDOZA, Money.of(9_000_000))).isEqualTo(new ShippingQuote.NoShipping());
    }

    @Test
    void givenFreeShippingFrom50000_whenTheSubtotalAfterTheDiscountIs50000OrMore_thenShippingCosts0() {
        store.saveSettings(new ShippingSettings(Optional.of(Money.of(5_000_000))), AT);

        assertThat(costs.quote(SANTA_FE, Money.of(5_000_000))).isEqualTo(new ShippingQuote.Cost(Money.zero()));
        assertThat(costs.quote(SANTA_FE, Money.of(4_999_999))).isEqualTo(new ShippingQuote.Cost(Money.of(450_000)));
    }

    @Test
    void givenThePublishedProvinces_thenTheyAreTheSameAsTheDomainOnes() {
        assertThat(Arrays.stream(com.pijamaparty.store.shipping.api.Province.values()).map(Enum::name))
            .containsExactlyElementsOf(Arrays.stream(Province.values()).map(Enum::name).toList());
    }
}
