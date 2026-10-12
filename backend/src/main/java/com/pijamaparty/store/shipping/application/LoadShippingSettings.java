package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingSettings;

/** Outbound port, read side of the shop's shipping settings (UC-12). */
public interface LoadShippingSettings {

    ShippingSettings loadSettings();
}
