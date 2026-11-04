package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-34 (T-E8-11): {@link CustomerProfiles} for the back office, read as the caller (the administrator). */
@Service
class CustomerProfilesService implements CustomerProfiles {

    private final LoadCustomerProfile profiles;

    CustomerProfilesService(LoadCustomerProfile profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> find(UUID customerId) {
        return profiles.find(customerId);
    }
}
