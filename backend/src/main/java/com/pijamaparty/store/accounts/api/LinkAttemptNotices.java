package com.pijamaparty.store.accounts.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): who gets the link attempt notice, the email that tells a customer someone tried to connect an
 * external account to its email. It carries no token. Joins the caller's transaction, which runs as {@code SYSTEM}.
 */
public interface LinkAttemptNotices {

    /** @return the customer's email and name; empty for a deleted account or one that is not a customer's */
    Optional<Recipient> recipientOf(UUID userId);

    /** Personal data (R-05): {@code toString()} does not reveal it. */
    record Recipient(String email, String name) {

        public Recipient {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return "Recipient[***]";
        }
    }
}
