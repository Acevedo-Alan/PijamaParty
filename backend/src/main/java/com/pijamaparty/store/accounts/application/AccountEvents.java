package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;

/** Outbound port: publishes the events of {@code accounts.api} in the caller's transaction. */
public interface AccountEvents {

    /** UC-19 (T-E4-02): the customer needs a verification email. */
    void emailVerificationRequested(UserId customer);

    /** UC-19 (T-E4-03): the customer needs a password reset email. */
    void passwordResetRequested(UserId customer);

    /** UC-27 (T-E4-14): the customer needs the email that confirms the link of its pending external account. */
    void linkConfirmationRequested(UserId customer);

    /** UC-27 (T-E4-14): someone typed the customer's email to connect an external account; nothing was linked. */
    void linkAttemptNoticeRequested(UserId customer);

    /** UC-28 (T-E4-16): the customer's account was deleted in this transaction. */
    void customerAccountDeleted(UserId customer);
}
