package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.ActivityValues;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SetFreeShippingThresholdService implements SetFreeShippingThreshold {

    private final LoadShippingSettings loadSettings;
    private final SaveShippingSettings saveSettings;
    private final LoadShippingZoneRates loadRates;
    private final ShippingActivity activity;
    private final Clock clock;

    SetFreeShippingThresholdService(LoadShippingSettings loadSettings, SaveShippingSettings saveSettings,
                                    LoadShippingZoneRates loadRates, ShippingActivity activity, Clock clock) {
        this.loadSettings = loadSettings;
        this.saveSettings = saveSettings;
        this.loadRates = loadRates;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<ShippingSettings> settings = ShippingSettings.withThreshold(command.thresholdCents());
        if (settings.isEmpty()) {
            return new Result.InvalidThreshold();
        }
        ShippingSettings before = loadSettings.loadSettings();
        saveSettings.saveSettings(settings.get(), clock.instant());
        // UC-37: saving the same threshold changes nothing worth recording.
        if (!before.equals(settings.get())) {
            activity.record(new ActivityEntry(command.actor().userId().orElseThrow(),
                ActivityAction.FREE_SHIPPING_THRESHOLD_CHANGED, ActivitySubject.of(SubjectType.SHIPPING),
                List.of(FieldChange.of("threshold",
                    before.freeShippingThreshold().map(ActivityValues::pesos).orElse(null),
                    settings.get().freeShippingThreshold().map(ActivityValues::pesos).orElse(null)))));
        }
        return new Result.Saved(new ShippingConfiguration(loadRates.loadRates(), settings.get()));
    }
}
