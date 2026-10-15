package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound write port (UC-17, T-E3-22): a sent email that never reached its recipient. Joins the caller's transaction,
 * which must exist.
 */
public interface BounceEmails {

    /**
     * The {@code SENT} email with this provider's id becomes {@code BOUNCED}.
     *
     * @return the email bounced now; empty when no {@code SENT} email has that id
     */
    Optional<Bounced> bounce(String providerMessageId, Instant at);

    record Bounced(EmailType type, UUID reference) {

        public Bounced {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reference, "reference");
        }
    }
}
