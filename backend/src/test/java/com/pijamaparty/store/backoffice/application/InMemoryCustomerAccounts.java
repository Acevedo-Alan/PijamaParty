package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link CustomerAccounts}: the customers' profiles the test gives; administrators are never added. */
public class InMemoryCustomerAccounts implements CustomerAccounts {

    private final Map<UUID, CustomerProfile> profiles = new ConcurrentHashMap<>();

    public void add(CustomerProfile profile) {
        profiles.put(profile.id(), profile);
    }

    public void remove(UUID customerId) {
        profiles.remove(customerId);
    }

    @Override
    public Optional<CustomerProfile> find(UUID customerId) {
        return Optional.ofNullable(profiles.get(customerId));
    }
}
