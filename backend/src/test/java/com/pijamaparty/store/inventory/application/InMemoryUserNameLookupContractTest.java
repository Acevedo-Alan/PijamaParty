package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryUserNameLookupContractTest extends UserNameLookupContractTest<InMemoryUserNameLookup> {

    @Override
    protected InMemoryUserNameLookup createSubject() {
        return new InMemoryUserNameLookup();
    }

    @Override
    protected UserId givenVisibleUser(String name) {
        UserId id = UserId.of(UUID.randomUUID());
        subject.add(id, name);
        return id;
    }
}
