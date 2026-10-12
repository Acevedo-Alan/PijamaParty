package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;

class InMemoryVariantAvailabilityContractTest extends VariantAvailabilityContractTest<InMemoryVariantAvailability> {

    @Override
    protected InMemoryVariantAvailability createSubject() {
        return new InMemoryVariantAvailability();
    }

    @Override
    protected void givenSold(VariantId variantId, Money price, Integer available) {
        subject.sell(variantId, price, available == null ? 0 : available);
    }

    @Override
    protected void givenNotSold(VariantId variantId, int available) {
        subject.stopSelling(variantId);
    }
}
