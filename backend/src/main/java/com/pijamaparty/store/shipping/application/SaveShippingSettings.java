package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingSettings;
import java.time.Instant;

/** Outbound port: writes the shop's shipping settings within the caller's transaction (UC-12). */
public interface SaveShippingSettings {

    /** Stores {@code settings}, changed {@code at}. */
    void saveSettings(ShippingSettings settings, Instant at);
}
