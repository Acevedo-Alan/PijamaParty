package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-19 (T-E4-03): a new password reset link for the email about to be sent, so its 30 minutes count from the
 * sending. The account keeps only the token's hash (RNF-29.6). Joins the caller's transaction, which runs as
 * {@code SYSTEM}.
 */
public interface PasswordResetLinks {

    /**
     * @return the recipient and the new token, which only its holder sees: put it in the link and forget it; empty,
     *     issuing nothing, when there is no such customer
     */
    Optional<PasswordResetLink> issue(UUID userId);

    /** Personal data (R-05) and a secret: {@code toString()} reveals neither. */
    record PasswordResetLink(String email, String name, AccountToken token) {

        public PasswordResetLink {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(token, "token");
        }

        @Override
        public String toString() {
            return "PasswordResetLink[***]";
        }
    }
}
