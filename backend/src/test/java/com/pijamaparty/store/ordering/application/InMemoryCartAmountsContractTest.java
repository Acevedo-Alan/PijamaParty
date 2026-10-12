package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.util.OptionalInt;

class InMemoryCartAmountsContractTest extends CartAmountsContractTest<InMemoryCartAmounts> {

    @Override
    protected InMemoryCartAmounts createSubject() {
        return new InMemoryCartAmounts();
    }

    @Override
    protected void givenGuestCart(Actor guest, long unitPriceCents, int quantity, int available,
                                  OptionalInt couponPercent) {
        if (available == 0) {
            return;
        }
        Money subtotal = Money.of(unitPriceCents).times(quantity);
        subject.put(guest, new CartAmounts.Amounts(subtotal, subtotal.percent(couponPercent.orElse(0))));
    }
}
