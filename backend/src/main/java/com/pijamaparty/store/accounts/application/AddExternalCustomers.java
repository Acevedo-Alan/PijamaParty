package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

/**
 * Outbound write port: inserts a customer created by a provider (UC-27), without a password, in the caller's
 * transaction, which runs as {@code SYSTEM} ({@link ExternalSignIns}).
 */
public interface AddExternalCustomers {

    /**
     * @return false, inserting nothing and without aborting the transaction, when the email is already registered
     */
    boolean add(User customer);
}
