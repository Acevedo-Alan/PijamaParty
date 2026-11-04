package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerProfiles.CustomerProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound read port of the back-office customer profile (UC-34): a user with role {@code CUSTOMER}, with its
 * addresses (the main one first) and the providers of its linked accounts. Within the caller's transaction, whose
 * security context decides what is visible.
 */
public interface LoadCustomerProfile {

    /** Empty when there is no such user, or it is not a customer. */
    Optional<CustomerProfile> find(UUID customerId);
}
