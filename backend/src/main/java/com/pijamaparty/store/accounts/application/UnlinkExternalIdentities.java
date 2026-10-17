package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalIdentity;

/**
 * Outbound write port: unlinks an external account (UC-27, T-E4-15) in the caller's transaction, as the customer that
 * owns it (V34).
 */
public interface UnlinkExternalIdentities {

    void unlink(ExternalIdentity identity);
}
