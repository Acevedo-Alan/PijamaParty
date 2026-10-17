package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * UC-22 (T-E4-08): the email of a customer once verified, so guest orders placed with it can join the account. Joins
 * the caller's transaction, so its security context decides which users are visible (RNF-27): the system sees every
 * one.
 */
public interface VerifiedEmails {

    /**
     * @return the customer's email, normalized (trimmed, lower case); empty when it is not verified, when the user is
     *     not a customer or is not visible. Personal data: never logged (R-05).
     */
    Optional<String> of(UserId customer);
}
