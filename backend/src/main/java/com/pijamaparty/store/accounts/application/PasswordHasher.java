package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordHash;

/** Outbound port: one-way password hashing (UC-04). */
public interface PasswordHasher {

    PasswordHash hash(String password);

    boolean matches(String password, PasswordHash hash);

    /**
     * A valid hash of no real password. Checking against it when the user does not exist costs the same time as a
     * real check, so the response time does not reveal which emails exist.
     */
    PasswordHash decoy();
}
