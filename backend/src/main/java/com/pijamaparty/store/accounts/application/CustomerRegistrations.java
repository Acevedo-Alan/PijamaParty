package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

/**
 * Outbound port for registration (UC-19). Nobody is signed in, so the implementation runs its own short transaction
 * as {@code SYSTEM}, which the database lets create only unverified customers with a password (V29). The password is
 * hashed before, outside any transaction (RNF-21).
 */
public interface CustomerRegistrations {

    /**
     * Inserts the new customer unless its email is already registered, and then runs {@code whenRegistered} in the
     * same transaction, so what it publishes commits or rolls back with the account.
     *
     * @return false, inserting and running nothing, when the email is already registered
     */
    boolean register(User customer, Runnable whenRegistered);
}
