package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import java.util.Objects;

/** Outbound port (RNF-23): the transactional email service. Never throws for what the service answers. */
public interface EmailSender {

    Outcome send(EmailMessage message);

    sealed interface Outcome {

        /** The service took the email; its id identifies it in the service's notifications (T-E3-22). */
        record Sent(String providerMessageId) implements Outcome {

            public Sent {
                Objects.requireNonNull(providerMessageId, "providerMessageId");
                if (providerMessageId.isBlank() || providerMessageId.length() > 200) {
                    throw new IllegalArgumentException("a provider message id has 1 to 200 characters");
                }
            }
        }

        /** The service refuses this email for good (an invalid recipient or content): trying again cannot help. */
        record Rejected() implements Outcome {}

        /** The service could not take it now (down, slow, limited, misconfigured): it may later. */
        record Unavailable() implements Outcome {}
    }
}
