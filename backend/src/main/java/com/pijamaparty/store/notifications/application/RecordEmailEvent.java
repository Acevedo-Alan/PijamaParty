package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-17, UC-18 (T-E3-22): the email provider notifies a bounce or a complaint. Recorded once per event id
 * (RNF-20.3); an email that never reached its recipient becomes {@code BOUNCED}, for the back-office alert. Runs as
 * {@code SYSTEM}, in one transaction.
 */
public interface RecordEmailEvent {

    Result execute(Command command);

    /**
     * @param eventId           the provider's id of the notification, the same on every retry of it
     * @param providerMessageId the provider's id of the email
     */
    record Command(MailProvider provider, String eventId, EmailEventType type, String providerMessageId) {

        public Command {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(type, "type");
            requireId(eventId, "eventId");
            requireId(providerMessageId, "providerMessageId");
        }

        private static void requireId(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isEmpty() || value.length() > 200) {
                throw new IllegalArgumentException(name + " must have 1 to 200 characters");
            }
        }
    }

    sealed interface Result {

        /** The email never reached its recipient and is now {@code BOUNCED}. */
        record Bounced(EmailType type, UUID reference) implements Result {

            public Bounced {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(reference, "reference");
            }
        }

        /** A transient bounce or a complaint: recorded, nothing else changes. */
        record Recorded(EmailEventType type) implements Result {

            public Recorded {
                Objects.requireNonNull(type, "type");
            }
        }

        /** It says an email bounced, but the outbox has no sent email with that provider's id. */
        record UnknownEmail() implements Result {}

        /** The notification was already recorded: nothing is done again. */
        record AlreadyProcessed() implements Result {}
    }
}
