package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class InMemoryOrderStatisticsContractTest extends OrderStatisticsContractTest<InMemoryOrderStatistics> {

    @Override
    protected InMemoryOrderStatistics createSubject() {
        return new InMemoryOrderStatistics();
    }

    @Override
    protected UUID givenOrder(OrderStatus status, Instant createdAt, Money total, List<Line> lines) {
        return subject.order(status, createdAt, total, lines.stream()
            .map(line -> new InMemoryOrderStatistics.Line(line.variantId(), line.quantity(), line.unitPrice()))
            .toList());
    }

    @Override
    protected void givenWithdrawal(UUID order, boolean received) {
        if (received) {
            subject.withdrawalReceived();
        }
    }
}
