package com.pijamaparty.store.accounts.application;

import java.io.Serial;

/**
 * Two returns from a provider with the same new external account raced, and the other one linked it first (UC-27).
 * Unexpected, so an exception: it rolls back the account this one created. The visitor is told the sign-in failed and
 * can simply try again.
 */
public final class ConcurrentExternalSignInException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    ConcurrentExternalSignInException() {
        super("the external account was linked by another sign-in");
    }
}
