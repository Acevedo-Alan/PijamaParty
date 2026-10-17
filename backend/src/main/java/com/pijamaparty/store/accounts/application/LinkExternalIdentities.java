package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalIdentity;

/** Outbound write port: links an external account to a customer (UC-27), in the caller's transaction. */
public interface LinkExternalIdentities {

    /**
     * @return false, linking nothing and without aborting the transaction, when the external account is already
     *     linked to a customer or the customer already has an account of that provider
     */
    boolean link(ExternalIdentity identity);
}
