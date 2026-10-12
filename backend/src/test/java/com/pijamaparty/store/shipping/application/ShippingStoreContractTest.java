package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LoadShippingZoneRates}, {@link SaveShippingZoneRates}, {@link LoadShippingSettings} and
 * {@link SaveShippingSettings} over the same storage (UC-12).
 */
public abstract class ShippingStoreContractTest<
    T extends LoadShippingZoneRates & SaveShippingZoneRates & LoadShippingSettings & SaveShippingSettings>
    extends ContractTest<T> {

    private static final Instant AT = Instant.parse("2026-10-12T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-13T12:00:00Z");

    @Test
    void givenNothingStored_whenLoading_thenNoProvinceHasShippingAndThereIsNoFreeShipping() {
        assertThat(subject.loadRates()).isEqualTo(ShippingZoneRates.none());
        assertThat(subject.loadSettings()).isEqualTo(ShippingSettings.withoutFreeShipping());
    }

    @Test
    void givenReplacedCosts_whenLoading_thenTheyAreTheSame() {
        ShippingZoneRates rates = ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.zero(),
            Province.CIUDAD_DE_BUENOS_AIRES, Money.of(650_000), Province.TIERRA_DEL_FUEGO, Money.of(1_000_000_000L)));

        subject.replaceRates(rates, AT);
        persistAndForget();

        assertThat(subject.loadRates()).isEqualTo(rates);
    }

    @Test
    void givenStoredCosts_whenReplacedByOthers_thenOnlyTheNewOnesRemain() {
        subject.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(300_000),
            Province.CORDOBA, Money.of(500_000))), AT);
        ShippingZoneRates replacement = ShippingZoneRates.of(Map.of(Province.CORDOBA, Money.of(550_000),
            Province.MENDOZA, Money.of(700_000)));

        subject.replaceRates(replacement, LATER);
        persistAndForget();

        assertThat(subject.loadRates()).isEqualTo(replacement);
    }

    @Test
    void givenStoredCosts_whenReplacedByNone_thenNoProvinceHasShipping() {
        subject.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(300_000))), AT);

        subject.replaceRates(ShippingZoneRates.none(), LATER);
        persistAndForget();

        assertThat(subject.loadRates()).isEqualTo(ShippingZoneRates.none());
    }

    @Test
    void givenAThreshold_whenSavedAndThenRemoved_thenEachIsLoadedBack() {
        ShippingSettings withThreshold = new ShippingSettings(Optional.of(Money.of(5_000_000)));

        subject.saveSettings(withThreshold, AT);
        persistAndForget();
        assertThat(subject.loadSettings()).isEqualTo(withThreshold);

        subject.saveSettings(ShippingSettings.withoutFreeShipping(), LATER);
        persistAndForget();
        assertThat(subject.loadSettings()).isEqualTo(ShippingSettings.withoutFreeShipping());
    }
}
