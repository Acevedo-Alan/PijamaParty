package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/** Outbound read port (UC-22, T-E4-08): a customer's email once verified, within the caller's transaction. */
@FunctionalInterface
public interface VerifiedCustomerEmails {

    /** @return the normalized email; empty when not verified or not a customer. Personal data: never logged (R-05). */
    Optional<String> of(UserId customer);
}
