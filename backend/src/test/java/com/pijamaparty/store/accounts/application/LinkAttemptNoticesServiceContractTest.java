package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.LinkAttemptNoticesContractTest;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/** UC-27 (T-E4-14): the real recipients, over the in-memory user store. */
class LinkAttemptNoticesServiceContractTest extends LinkAttemptNoticesContractTest<LinkAttemptNoticesService> {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();

    @Override
    protected LinkAttemptNoticesService createSubject() {
        return new LinkAttemptNoticesService(users);
    }

    @Override
    protected UUID customer(String email, String name) {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email(email), name,
            new InMemoryPasswordHasher().hash("ocho1234"), NOW);
        users.save(customer);
        return customer.id().value();
    }

    @Override
    protected UUID administrator(String email) {
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email(email), "Owner",
            new InMemoryPasswordHasher().hash("doce12345678"), NOW);
        users.save(administrator);
        return administrator.id().value();
    }
}
