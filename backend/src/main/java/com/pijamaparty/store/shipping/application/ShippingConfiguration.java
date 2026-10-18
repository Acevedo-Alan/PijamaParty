package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.util.Objects;

/**
 * The cost of each province and the free-shipping threshold: what the back office edits (UC-12) and what the
 * storefront shows before a purchase (RNF-31.6).
 */
public record ShippingConfiguration(ShippingZoneRates rates, ShippingSettings settings) {

    public ShippingConfiguration {
        Objects.requireNonNull(rates, "rates");
        Objects.requireNonNull(settings, "settings");
    }
}
