package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** RNF-31.6 (T-E5-12): the public shipping costs, over the in-memory store. */
class GetShippingRatesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T15:00:00Z");

    private final InMemoryShippingStore store = new InMemoryShippingStore();
    private final GetShippingRatesService getRates = new GetShippingRatesService(store, store);

    @Test
    void givenNothingConfigured_whenAVisitorReadsTheRates_thenNoProvinceHasShippingNorFreeShipping() {
        assertThat(getRates.execute()).isEqualTo(
            new ShippingConfiguration(ShippingZoneRates.none(), ShippingSettings.withoutFreeShipping()));
    }

    @Test
    void givenCostsAndAThreshold_whenAVisitorReadsTheRates_thenItGetsTheSameOnesTheCheckoutCharges() {
        ShippingZoneRates rates = ShippingZoneRates.of(
            Map.of(Province.SANTA_FE, Money.of(350_000), Province.CORDOBA, Money.zero()));
        ShippingSettings settings = new ShippingSettings(Optional.of(Money.of(5_000_000)));
        store.replaceRates(rates, NOW);
        store.saveSettings(settings, NOW);

        assertThat(getRates.execute()).isEqualTo(new ShippingConfiguration(rates, settings));
    }
}
