package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryGuestOrderLinksContractTest extends GuestOrderLinksContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected UserId verifiedCustomer(String email) {
        return UserId.of(UUID.randomUUID());
    }

    @Override
    protected void unlinked(OrderId order) {
        subject.unlink(order);
    }

    @Override
    protected void emailCorrected(OrderId order, String email) {
        subject.replace(order, email, NOW);
    }
}
