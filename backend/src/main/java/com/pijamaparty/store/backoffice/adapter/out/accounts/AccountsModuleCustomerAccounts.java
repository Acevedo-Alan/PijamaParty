package com.pijamaparty.store.backoffice.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.CustomerProfiles;
import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import com.pijamaparty.store.backoffice.application.CustomerAccounts;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads a customer's account through the public API of the accounts module, in the caller's transaction. */
@Component
class AccountsModuleCustomerAccounts implements CustomerAccounts {

    private final CustomerProfiles profiles;

    AccountsModuleCustomerAccounts(CustomerProfiles profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<CustomerProfile> find(UUID customerId) {
        return profiles.find(customerId);
    }
}
