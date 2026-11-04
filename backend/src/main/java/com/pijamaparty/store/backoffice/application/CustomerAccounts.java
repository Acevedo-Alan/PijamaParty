package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound read port (UC-34): a customer's account as {@code accounts.api.CustomerProfiles} reads it, in the caller's
 * transaction.
 */
public interface CustomerAccounts {

    /** Empty when there is no customer with that id. */
    Optional<CustomerProfile> find(UUID customerId);
}
