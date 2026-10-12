package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.domain.Province;

class InMemoryShippingCostCalculatorContractTest
    extends ShippingCostCalculatorContractTest<InMemoryShippingCostCalculator> {

    @Override
    protected InMemoryShippingCostCalculator createSubject() {
        return new InMemoryShippingCostCalculator();
    }

    @Override
    protected void givenShipping(Province province, long costCents) {
        subject.ship(province, Money.of(costCents));
    }
}
