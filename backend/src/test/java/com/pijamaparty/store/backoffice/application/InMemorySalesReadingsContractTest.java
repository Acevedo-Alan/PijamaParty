package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.UUID;

class InMemorySalesReadingsContractTest extends SalesReadingsContractTest<InMemorySalesReadings> {

    @Override
    protected InMemorySalesReadings createSubject() {
        return new InMemorySalesReadings();
    }

    @Override
    protected void givenOrder(boolean sale, Instant createdAt, long totalCents, UUID variant, int units,
                              long unitCents) {
        if (sale) {
            subject.sold(createdAt, Money.of(totalCents), variant, units, Money.of(unitCents * units));
        }
    }
}
