package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Fake of {@link ShippingCostCalculator}: a fixed cost per province. */
public class InMemoryShippingCostCalculator implements ShippingCostCalculator {

    private final Map<Province, Money> costs = new EnumMap<>(Province.class);

    public void ship(Province province, Money cost) {
        costs.put(province, cost);
    }

    @Override
    public Optional<Money> costTo(Province province) {
        return Optional.ofNullable(costs.get(province));
    }
}
