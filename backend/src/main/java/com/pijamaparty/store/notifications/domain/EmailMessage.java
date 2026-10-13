package com.pijamaparty.store.notifications.domain;

import java.util.Objects;

/**
 * An email ready to send: one recipient, a subject, and the same content as HTML and as plain text. Personal data
 * (R-05): {@code toString()} prints the subject only.
 */
public record EmailMessage(String to, String subject, String html, String text) {

    public EmailMessage {
        requireText(to, "to");
        requireText(subject, "subject");
        requireText(html, "html");
        requireText(text, "text");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }

    @Override
    public String toString() {
        return "EmailMessage[to=***, subject=" + subject + "]";
    }
}
