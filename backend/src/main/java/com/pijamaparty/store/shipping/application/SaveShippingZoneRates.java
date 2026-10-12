package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;

/** Outbound port: writes the shipping costs per province within the caller's transaction (UC-12). */
public interface SaveShippingZoneRates {

    /**
     * Replaces every cost: the provinces of {@code rates} get theirs, changed {@code at} unless it stays the same, and
     * every other province is left without shipping.
     */
    void replaceRates(ShippingZoneRates rates, Instant at);
}
