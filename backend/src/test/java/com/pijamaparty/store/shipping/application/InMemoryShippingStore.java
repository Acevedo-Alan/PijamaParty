package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;

/** In-memory shipping costs and settings, for the use-case tests; it passes the same contract as the JDBC adapter. */
public class InMemoryShippingStore
    implements LoadShippingZoneRates, SaveShippingZoneRates, LoadShippingSettings, SaveShippingSettings {

    private ShippingZoneRates rates = ShippingZoneRates.none();
    private ShippingSettings settings = ShippingSettings.withoutFreeShipping();

    @Override
    public ShippingZoneRates loadRates() {
        return rates;
    }

    @Override
    public void replaceRates(ShippingZoneRates rates, Instant at) {
        this.rates = rates;
    }

    @Override
    public ShippingSettings loadSettings() {
        return settings;
    }

    @Override
    public void saveSettings(ShippingSettings settings, Instant at) {
        this.settings = settings;
    }
}
