package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Objects;
import java.util.UUID;

/**
 * RNF-23, UC-17: sends the next email due from the outbox. Not transactional as a whole: no connection waits while
 * the email service answers (RNF-21). Runs as {@code SYSTEM}. An email the service does not take is retried at 1, 5,
 * 15 and 60 minutes, and fails after 5 attempts or when the service rejects it. At least once: an email whose
 * acceptance could not be recorded is sent again at its next attempt.
 */
public interface SendNextEmail {

    Result execute();

    sealed interface Result {

        record Sent(EmailType type, UUID reference) implements Result {

            public Sent {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(reference, "reference");
            }
        }

        /** Not taken this time; tried again later. */
        record Retrying(EmailType type, UUID reference, int attempt) implements Result {

            public Retrying {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(reference, "reference");
            }
        }

        /** Given up: it will not be sent. */
        record Failed(EmailType type, UUID reference) implements Result {

            public Failed {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(reference, "reference");
            }
        }

        record NoneDue() implements Result {}
    }
}
