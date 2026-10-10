package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

/** Outbound port: inserts or updates a user within the caller's transaction and security context. */
public interface SaveUser {

    void save(User user);
}
