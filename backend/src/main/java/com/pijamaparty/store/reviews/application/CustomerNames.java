package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * Outbound read port (UC-24), backed by {@code accounts.api}: the signed-in customer's account name, read as that
 * customer in the caller's transaction. Personal data (R-05): never logged.
 */
public interface CustomerNames {

    /** Empty when the account no longer exists, or the actor cannot see it. */
    Optional<String> nameOf(UserId customer);
}
