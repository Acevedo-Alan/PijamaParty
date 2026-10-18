package com.pijamaparty.store.shipping.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetShippingRatesService implements GetShippingRates {

    private final LoadShippingZoneRates loadRates;
    private final LoadShippingSettings loadSettings;

    GetShippingRatesService(LoadShippingZoneRates loadRates, LoadShippingSettings loadSettings) {
        this.loadRates = loadRates;
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingConfiguration execute() {
        return new ShippingConfiguration(loadRates.loadRates(), loadSettings.loadSettings());
    }
}
