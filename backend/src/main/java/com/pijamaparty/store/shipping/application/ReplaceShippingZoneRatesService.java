package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.ActivityValues;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReplaceShippingZoneRatesService implements ReplaceShippingZoneRates {

    private final LoadShippingZoneRates loadRates;
    private final SaveShippingZoneRates saveRates;
    private final LoadShippingSettings loadSettings;
    private final ShippingActivity activity;
    private final Clock clock;

    ReplaceShippingZoneRatesService(LoadShippingZoneRates loadRates, SaveShippingZoneRates saveRates,
                                    LoadShippingSettings loadSettings, ShippingActivity activity, Clock clock) {
        this.loadRates = loadRates;
        this.saveRates = saveRates;
        this.loadSettings = loadSettings;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return switch (ShippingZoneRates.parse(command.rates())) {
            case ShippingZoneRates.Parsed.Invalid invalid -> new Result.InvalidProvinces(invalid.provinces());
            case ShippingZoneRates.Parsed.Valid valid -> {
                ShippingZoneRates before = loadRates.loadRates();
                saveRates.replaceRates(valid.rates(), clock.instant());
                record(command.actor(), before, valid.rates());
                yield new Result.Replaced(new ShippingConfiguration(valid.rates(), loadSettings.loadSettings()));
            }
        };
    }

    /** UC-37: one entry per province whose cost changed, labelled with the province; none when nothing changed. */
    private void record(Actor administrator, ShippingZoneRates before, ShippingZoneRates after) {
        for (Province province : Province.values()) {
            Optional<Money> from = before.costOf(province);
            Optional<Money> to = after.costOf(province);
            if (!from.equals(to)) {
                activity.record(new ActivityEntry(administrator.userId().orElseThrow(),
                    ActivityAction.SHIPPING_ZONES_CHANGED,
                    new ActivitySubject(SubjectType.SHIPPING, Optional.empty(), Optional.of(province.name())),
                    List.of(FieldChange.of("cost", from.map(ActivityValues::pesos).orElse(null),
                        to.map(ActivityValues::pesos).orElse(null)))));
            }
        }
    }
}
