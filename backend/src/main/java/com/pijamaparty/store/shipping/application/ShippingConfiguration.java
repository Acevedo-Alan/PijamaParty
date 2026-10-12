package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.util.Objects;

/** What the back office shows of shipping (UC-12): the cost of each province and the free-shipping threshold. */
public record ShippingConfiguration(ShippingZoneRates rates, ShippingSettings settings) {

    public ShippingConfiguration {
        Objects.requireNonNull(rates, "rates");
        Objects.requireNonNull(settings, "settings");
    }
}
