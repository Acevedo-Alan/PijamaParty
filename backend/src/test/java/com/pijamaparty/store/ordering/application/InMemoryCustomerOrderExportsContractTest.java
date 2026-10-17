package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryCustomerOrderExportsContractTest extends CustomerOrderExportsContractTest<InMemoryOrderStore> {

    @Override
    protected InMemoryOrderStore createSubject() {
        return new InMemoryOrderStore();
    }

    @Override
    protected UserId customer(String email) {
        return UserId.of(UUID.randomUUID());
    }
}
