package com.pijamaparty.store.accounts.domain;

/** A provider a customer signs in with (UC-27; column {@code provider}, V34). Apple comes in a later phase. */
public enum ExternalProvider {

    /** Vouches for the emails it returns ({@code email_verified}). */
    GOOGLE(true),

    /** Does not say whether the email is verified: it is confirmed by email before linking. */
    FACEBOOK(false);

    private final boolean vouchesForEmails;

    ExternalProvider(boolean vouchesForEmails) {
        this.vouchesForEmails = vouchesForEmails;
    }

    /** Whether an email this provider says is verified counts as verified (UC-27). */
    public boolean vouchesForEmails() {
        return vouchesForEmails;
    }
}
