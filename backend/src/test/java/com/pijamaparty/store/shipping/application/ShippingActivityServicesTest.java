package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-37 (T-E8-03): what the shipping use cases record in the activity log, and when they record nothing. */
class ShippingActivityServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);

    private final InMemoryShippingStore store = new InMemoryShippingStore();
    private final InMemoryShippingActivity activity = new InMemoryShippingActivity();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReplaceShippingZoneRates replace =
        new ReplaceShippingZoneRatesService(store, store, store, activity, clock);
    private final SetFreeShippingThreshold setThreshold =
        new SetFreeShippingThresholdService(store, store, store, activity, clock);

    @Test
    void givenStoredCosts_whenReplaced_thenEachProvinceWhoseCostChangedHasItsOwnEntry() {
        store.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(500_000),
            Province.MENDOZA, Money.of(900_000), Province.CORDOBA, Money.of(650_000))), NOW);

        replace.execute(new ReplaceShippingZoneRates.Command(ADMIN, List.of(
            new ShippingZoneRates.TypedRate(Province.SANTA_FE, 600_000),
            new ShippingZoneRates.TypedRate(Province.CORDOBA, 650_000),
            new ShippingZoneRates.TypedRate(Province.SALTA, 1_200_000))));

        assertThat(activity.entries()).containsExactly(
            zone("MENDOZA", "$9.000", null),
            zone("SALTA", null, "$12.000"),
            zone("SANTA_FE", "$5.000", "$6.000"));
    }

    @Test
    void givenStoredCosts_whenTheSameOnesAreSavedOrInvalidOnesAreSent_thenNothingIsRecorded() {
        store.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(500_000))), NOW);

        replace.execute(new ReplaceShippingZoneRates.Command(ADMIN,
            List.of(new ShippingZoneRates.TypedRate(Province.SANTA_FE, 500_000))));
        replace.execute(new ReplaceShippingZoneRates.Command(ADMIN,
            List.of(new ShippingZoneRates.TypedRate(Province.SANTA_FE, -1))));

        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenNoFreeShipping_whenAThresholdIsSetChangedAndRemoved_thenEachChangeIsRecordedOnce() {
        setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(5_000_000L)));
        setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(5_000_000L)));
        setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(6_000_000L)));
        setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.empty()));
        setThreshold.execute(new SetFreeShippingThreshold.Command(ADMIN, Optional.of(0L)));

        assertThat(activity.entries()).extracting(ActivityEntry::changes).containsExactly(
            List.of(FieldChange.of("threshold", null, "$50.000")),
            List.of(FieldChange.of("threshold", "$50.000", "$60.000")),
            List.of(FieldChange.of("threshold", "$60.000", null)));
        assertThat(activity.entries()).extracting(ActivityEntry::subject)
            .containsOnly(ActivitySubject.of(SubjectType.SHIPPING));
        assertThat(store.loadSettings()).isEqualTo(ShippingSettings.withoutFreeShipping());
    }

    private static ActivityEntry zone(String province, String before, String after) {
        return new ActivityEntry(ADMIN_ID, ActivityAction.SHIPPING_ZONES_CHANGED,
            new ActivitySubject(SubjectType.SHIPPING, Optional.empty(), Optional.of(province)),
            List.of(FieldChange.of("cost", before, after)));
    }
}
