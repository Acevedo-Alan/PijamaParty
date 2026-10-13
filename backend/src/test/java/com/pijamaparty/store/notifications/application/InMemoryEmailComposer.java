package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link EmailComposer}: the email arranged for each reference, {@code Gone} for any other. */
public class InMemoryEmailComposer implements EmailComposer {

    private final EmailType type;
    private final Map<UUID, EmailMessage> messages = new ConcurrentHashMap<>();

    public InMemoryEmailComposer(EmailType type) {
        this.type = type;
    }

    /** Test arrangement: what the email about {@code reference} says, and to whom. */
    public EmailMessage writes(UUID reference) {
        var message = new EmailMessage(type.name().toLowerCase() + "@example.com", type + " about " + reference,
            "<p>" + reference + "</p>", reference.toString());
        messages.put(reference, message);
        return message;
    }

    @Override
    public EmailType type() {
        return type;
    }

    @Override
    public Composition compose(UUID reference) {
        EmailMessage message = messages.get(reference);
        return message == null ? new Composition.Gone() : new Composition.Composed(message);
    }
}
