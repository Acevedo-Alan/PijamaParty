package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReplaceShippingZoneRatesService implements ReplaceShippingZoneRates {

    private final SaveShippingZoneRates saveRates;
    private final LoadShippingSettings loadSettings;
    private final Clock clock;

    ReplaceShippingZoneRatesService(SaveShippingZoneRates saveRates, LoadShippingSettings loadSettings, Clock clock) {
        this.saveRates = saveRates;
        this.loadSettings = loadSettings;
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
                saveRates.replaceRates(valid.rates(), clock.instant());
                yield new Result.Replaced(new ShippingConfiguration(valid.rates(), loadSettings.loadSettings()));
            }
        };
    }
}
