package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetShippingConfigurationService implements GetShippingConfiguration {

    private final LoadShippingZoneRates loadRates;
    private final LoadShippingSettings loadSettings;

    GetShippingConfigurationService(LoadShippingZoneRates loadRates, LoadShippingSettings loadSettings) {
        this.loadRates = loadRates;
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Found(new ShippingConfiguration(loadRates.loadRates(), loadSettings.loadSettings()));
    }
}
