package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Buyer;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemoryOrderDetailsContractTest extends OrderDetailsContractTest<InMemoryOrderDetails> {

    @Override
    protected InMemoryOrderDetails createSubject() {
        return new InMemoryOrderDetails();
    }

    @Override
    protected void givenOrderAwaitingPayment(UUID orderId, long number) {
        Instant now = Instant.parse("2026-10-13T18:00:00Z");
        subject.add(new AdminOrder(orderId, number, Status.AWAITING_PAYMENT, List.of(Status.CANCELLED), now, now,
            Optional.empty(), new Buyer("Ana Pérez", "ana@example.com", "3425551234", "12345678"),
            new Address("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "SANTA_FE", "3000"),
            List.of(), Money.of(2_000_000), Money.zero(), Optional.empty(), Money.of(300_000), Money.of(2_300_000),
            "MERCADOPAGO", Optional.empty(), false));
    }
}
