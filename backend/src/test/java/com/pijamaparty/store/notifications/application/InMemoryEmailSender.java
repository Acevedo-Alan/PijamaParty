package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Fake of {@link EmailSender}: takes every email with a numbered id, unless told to reject or to be down. */
public class InMemoryEmailSender implements EmailSender {

    public enum Mode { ACCEPT, REJECT, DOWN }

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.ACCEPT);
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<EmailMessage> attempted = new CopyOnWriteArrayList<>();
    private final List<EmailMessage> delivered = new CopyOnWriteArrayList<>();

    @Override
    public Outcome send(EmailMessage message) {
        attempted.add(message);
        return switch (mode.get()) {
            case ACCEPT -> {
                delivered.add(message);
                yield new Outcome.Sent("email-" + sequence.incrementAndGet());
            }
            case REJECT -> new Outcome.Rejected();
            case DOWN -> new Outcome.Unavailable();
        };
    }

    public void mode(Mode newMode) {
        mode.set(newMode);
    }

    /** Every email it was asked to send, taken or not. */
    public List<EmailMessage> attempted() {
        return List.copyOf(attempted);
    }

    /** The emails it took. */
    public List<EmailMessage> delivered() {
        return List.copyOf(delivered);
    }
}
