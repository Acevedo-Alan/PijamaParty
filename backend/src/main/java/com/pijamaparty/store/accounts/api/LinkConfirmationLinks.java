package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): a new link confirmation link for the email about to be sent, so its 24 hours count from the
 * sending. It links the external account waiting for the customer. The account keeps only the token's hash
 * (RNF-29.6). Joins the caller's transaction, which runs as {@code SYSTEM}.
 */
public interface LinkConfirmationLinks {

    /**
     * @return the recipient, the provider and the new token, which only its holder sees: put it in the link and
     *     forget it; empty, issuing nothing, when there is no such customer or no external account waits for it
     */
    Optional<LinkConfirmationLink> issue(UUID userId);

    /** The provider of the external account to link. */
    enum Provider {
        GOOGLE,
        FACEBOOK
    }

    /** Personal data (R-05) and a secret: {@code toString()} reveals neither. */
    record LinkConfirmationLink(String email, String name, Provider provider, AccountToken token) {

        public LinkConfirmationLink {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(token, "token");
        }

        @Override
        public String toString() {
            return "LinkConfirmationLink[provider=" + provider + ", ***]";
        }
    }
}
