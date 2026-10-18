package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.UUID;

class InMemoryVariantAvailabilityContractTest extends VariantAvailabilityContractTest<InMemoryVariantAvailability> {

    @Override
    protected InMemoryVariantAvailability createSubject() {
        return new InMemoryVariantAvailability();
    }

    @Override
    protected void givenSold(VariantId variantId, UUID productId, UUID categoryId, Money price, Integer available) {
        subject.sell(variantId, productId, categoryId, price, available == null ? 0 : available);
    }

    @Override
    protected void givenNotSold(VariantId variantId, int available) {
        subject.stopSelling(variantId);
    }
}
