package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SavedAddress;
import com.pijamaparty.store.accounts.api.CustomerProfiles.SignInProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemoryCustomerAccountsContractTest extends CustomerAccountsContractTest<InMemoryCustomerAccounts> {

    @Override
    protected InMemoryCustomerAccounts createSubject() {
        return new InMemoryCustomerAccounts();
    }

    @Override
    protected UUID givenCustomer(String name, String email, SavedAddress address) {
        UUID id = UUID.randomUUID();
        subject.add(new CustomerProfile(id, name, email, true, Optional.of("3425551234"), T0,
            List.of(SignInProvider.GOOGLE), List.of(address)));
        return id;
    }

    @Override
    protected UUID givenAdministrator() {
        return UUID.randomUUID();
    }
}
