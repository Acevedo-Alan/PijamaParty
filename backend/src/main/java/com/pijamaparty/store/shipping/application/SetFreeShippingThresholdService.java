package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SetFreeShippingThresholdService implements SetFreeShippingThreshold {

    private final SaveShippingSettings saveSettings;
    private final LoadShippingZoneRates loadRates;
    private final Clock clock;

    SetFreeShippingThresholdService(SaveShippingSettings saveSettings, LoadShippingZoneRates loadRates, Clock clock) {
        this.saveSettings = saveSettings;
        this.loadRates = loadRates;
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
        saveSettings.saveSettings(settings.get(), clock.instant());
        return new Result.Saved(new ShippingConfiguration(loadRates.loadRates(), settings.get()));
    }
}
