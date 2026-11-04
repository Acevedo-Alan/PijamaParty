package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import java.time.Instant;
import java.util.UUID;

class InMemoryLoadCustomerProfileContractTest extends LoadCustomerProfileContractTest<InMemoryLoadCustomerProfile> {

    @Override
    protected InMemoryLoadCustomerProfile createSubject() {
        return new InMemoryLoadCustomerProfile();
    }

    @Override
    protected void givenUser(UUID id, String name, String email, boolean emailVerified, String phone, String role,
                             Instant registeredAt) {
        subject.addUser(id, name, email, emailVerified, phone, role, registeredAt);
    }

    @Override
    protected void givenAddress(UUID userId, SavedAddress address, Instant addedAt) {
        subject.addAddress(userId, address, addedAt);
    }

    @Override
    protected void givenLinkedAccount(UUID userId, SignInProvider provider, Instant linkedAt) {
        subject.link(userId, provider, linkedAt);
    }
}
