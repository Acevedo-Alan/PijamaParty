package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;

/**
 * Outbound write port (UC-28): deletes a customer's account within the caller's transaction, as that customer (V36);
 * its addresses, tokens, external accounts and waiting link go with it through their foreign keys.
 */
public interface DeleteCustomers {

    /** @return whether it was there */
    boolean delete(UserId customer);
}
