package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** The zone table over the in-memory store, which passes the same contract as the JDBC adapter. */
class ZoneTableShippingCostCalculatorContractTest
    extends ShippingCostCalculatorContractTest<ZoneTableShippingCostCalculator> {

    private final InMemoryShippingStore store = new InMemoryShippingStore();
    private final Map<Province, Money> rates = new EnumMap<>(Province.class);

    @Override
    protected ZoneTableShippingCostCalculator createSubject() {
        return new ZoneTableShippingCostCalculator(store);
    }

    @Override
    protected void givenShipping(Province province, long costCents) {
        rates.put(province, Money.of(costCents));
        store.replaceRates(ShippingZoneRates.of(rates), Instant.parse("2026-10-12T15:00:00Z"));
    }
}
