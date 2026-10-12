package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;

class InMemoryShippingQuotesContractTest extends ShippingQuotesContractTest<InMemoryShippingQuotes> {

    @Override
    protected InMemoryShippingQuotes createSubject() {
        return new InMemoryShippingQuotes();
    }

    @Override
    protected void givenRate(Province province, long costCents) {
        subject.rate(province, Money.of(costCents));
    }

    @Override
    protected void givenFreeShippingFrom(long thresholdCents) {
        subject.freeShippingFrom(Money.of(thresholdCents));
    }
}
