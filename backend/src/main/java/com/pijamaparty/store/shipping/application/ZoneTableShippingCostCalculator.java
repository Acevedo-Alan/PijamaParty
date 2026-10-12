package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** UC-12: the cost the administrator set for the province, in the caller's transaction. */
@Component
class ZoneTableShippingCostCalculator implements ShippingCostCalculator {

    private final LoadShippingZoneRates loadRates;

    ZoneTableShippingCostCalculator(LoadShippingZoneRates loadRates) {
        this.loadRates = loadRates;
    }

    @Override
    public Optional<Money> costTo(Province province) {
        return loadRates.loadRates().costOf(province);
    }
}
