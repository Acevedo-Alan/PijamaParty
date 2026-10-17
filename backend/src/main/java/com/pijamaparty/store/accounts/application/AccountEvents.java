package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;

/** Outbound port: publishes the events of {@code accounts.api} in the caller's transaction. */
public interface AccountEvents {

    /** UC-19 (T-E4-02): the customer needs a verification email. */
    void emailVerificationRequested(UserId customer);

    /** UC-19 (T-E4-03): the customer needs a password reset email. */
    void passwordResetRequested(UserId customer);
}
