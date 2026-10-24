package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-12: the back-office use cases of shipping, over the in-memory store. */
class ShippingAdminServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final Actor CUSTOMER = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(),
        Optional.empty());

    private final InMemoryShippingStore store = new InMemoryShippingStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GetShippingConfigurationService get = new GetShippingConfigurationService(store, store);
    private final InMemoryShippingActivity activity = new InMemoryShippingActivity();
    private final ReplaceShippingZoneRatesService replace =
        new ReplaceShippingZoneRatesService(store, store, store, activity, clock);
    private final SetFreeShippingThresholdService setThreshold =
        new SetFreeShippingThresholdService(store, store, store, activity, clock);

    @Test
    void givenNothingConfigured_whenTheAdministratorReadsIt_thenNoProvinceHasShippingNorFreeShipping() {
        assertThat(get.execute(new GetShippingConfiguration.Query(ADMIN))).isEqualTo(
            new GetShippingConfiguration.Result.Found(new ShippingConfiguration(ShippingZoneRates.none(),
                ShippingSettings.withoutFreeShipping())));
    }

    @Test
    void givenStoredCosts_whenTheAdministratorReplacesThem_thenOnlyTheListedProvincesHaveShipping() {
        store.replaceRates(ShippingZoneRates.of(Map.of(Province.MENDOZA, Money.of(900_000))), NOW);
        store.saveSettings(new ShippingSettings(Optional.of(Money.of(5_000_000))), NOW);

        ReplaceShippingZoneRates.Result result = replace.execute(new ReplaceShippingZoneRates.Command(ADMIN, List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, 0),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 650_000))));

        ShippingConfiguration expected = new ShippingConfiguration(
            ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.zero(), Province.CORDOBA, Money.of(650_000))),
            new ShippingSettings(Optional.of(Money.of(5_000_000))));
        assertThat(result).isEqualTo(new ReplaceShippingZoneRates.Result.Replaced(expected));
        assertThat(get.execute(new GetShippingConfiguration.Query(ADMIN)))
            .isEqualTo(new GetShippingConfiguration.Result.Found(expected));
        assertThat(store.loadRates().costOf(Province.MENDOZA)).isEmpty();
    }

    @Test
    void givenInvalidCosts_whenReplacing_thenTheProvincesAreNamedAndNothingIsSaved() {
        ShippingZoneRates stored = ShippingZoneRates.of(Map.of(Province.MENDOZA, Money.of(900_000)));
        store.replaceRates(stored, NOW);

        ReplaceShippingZoneRates.Result result = replace.execute(new ReplaceShippingZoneRates.Command(ADMIN, List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, -1),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 1_000_000_001L),
            new ShippingZoneRates.TypedRate(Province.SALTA, 100),
            new ShippingZoneRates.TypedRate(Province.SALTA, 200),
            new ShippingZoneRates.TypedRate(Province.JUJUY, 100))));

        assertThat(result).isEqualTo(new ReplaceShippingZoneRates.Result.InvalidProvinces(
            Set.of(Province.SANTA_FE, Province.CORDOBA, Province.SALTA)));
        assertThat(store.loadRates()).isEqualTo(stored);
    }

    @Test
    void givenAThreshold_whenTheAdministratorSetsIt_thenItIsKeptWithTheCosts() {
        ShippingZoneRates stored = ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(300_000)));
        store.replaceRates(stored, NOW);

        assertThat(setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(5_000_000L))))
            .isEqualTo(new SetFreeShippingThreshold.Result.Saved(new ShippingConfiguration(stored,
                new ShippingSettings(Optional.of(Money.of(5_000_000))))));
        assertThat(store.loadSettings().freeShippingThreshold()).contains(Money.of(5_000_000));
    }

    @Test
    void givenAThreshold_whenTheAdministratorRemovesIt_thenThereIsNoFreeShipping() {
        store.saveSettings(new ShippingSettings(Optional.of(Money.of(5_000_000))), NOW);

        assertThat(setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.empty())))
            .isEqualTo(new SetFreeShippingThreshold.Result.Saved(new ShippingConfiguration(ShippingZoneRates.none(),
                ShippingSettings.withoutFreeShipping())));
        assertThat(store.loadSettings()).isEqualTo(ShippingSettings.withoutFreeShipping());
    }

    @Test
    void givenAThresholdOutOfRange_whenSet_thenItIsRefusedAndTheStoredOneStays() {
        ShippingSettings stored = new ShippingSettings(Optional.of(Money.of(5_000_000)));
        store.saveSettings(stored, NOW);

        assertThat(setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(0L))))
            .isEqualTo(new SetFreeShippingThreshold.Result.InvalidThreshold());
        assertThat(setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(1_000_000_001L))))
            .isEqualTo(new SetFreeShippingThreshold.Result.InvalidThreshold());
        assertThat(store.loadSettings()).isEqualTo(stored);
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenConfiguringShipping_thenItIsForbiddenAndNothingChanges() {
        assertThat(get.execute(new GetShippingConfiguration.Query(CUSTOMER)))
            .isEqualTo(new GetShippingConfiguration.Result.Forbidden());
        assertThat(replace.execute(new ReplaceShippingZoneRates.Command(CUSTOMER,
            List.of(new ShippingZoneRates.TypedRate(Province.SANTA_FE, 0)))))
            .isEqualTo(new ReplaceShippingZoneRates.Result.Forbidden());
        assertThat(setThreshold.execute(new SetFreeShippingThreshold.Command(CUSTOMER, Optional.of(1L))))
            .isEqualTo(new SetFreeShippingThreshold.Result.Forbidden());
        assertThat(store.loadRates()).isEqualTo(ShippingZoneRates.none());
        assertThat(store.loadSettings()).isEqualTo(ShippingSettings.withoutFreeShipping());
    }
}
