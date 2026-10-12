package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.shipping.domain.ShippingZoneRates;

/** Outbound port, read side of the shipping costs per province (UC-12). */
public interface LoadShippingZoneRates {

    ShippingZoneRates loadRates();
}
