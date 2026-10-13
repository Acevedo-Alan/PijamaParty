package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbound port, a strategy per type of email (Open/Closed): writes the email about its reference when it is sent,
 * so the outbox holds no personal data.
 */
public interface EmailComposer {

    EmailType type();

    Composition compose(UUID reference);

    sealed interface Composition {

        record Composed(EmailMessage message) implements Composition {

            public Composed {
                Objects.requireNonNull(message, "message");
            }
        }

        /** There is nothing to tell, or no one to tell it to: the email cannot be sent, now or later. */
        record Gone() implements Composition {}
    }
}
