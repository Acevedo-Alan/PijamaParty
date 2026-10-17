package com.pijamaparty.store.accounts.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An account at a provider (UC-27): the provider and its identifier of the account ({@code sub} of Google, {@code id}
 * of Facebook), printable ASCII without spaces, at most 255 characters (V34). A pseudonymous identifier of a person:
 * {@link #toString()} does not reveal it.
 */
public record ExternalAccount(ExternalProvider provider, String subject) {

    private static final Pattern SUBJECT = Pattern.compile("[!-~]{1,255}");

    public ExternalAccount {
        Objects.requireNonNull(provider, "provider");
        if (!isValidSubject(subject)) {
            throw new IllegalArgumentException("a subject is printable ASCII without spaces, 1 to 255 characters");
        }
    }

    /** What the provider returned; empty when it cannot be an account identifier. */
    public static Optional<ExternalAccount> of(ExternalProvider provider, String subject) {
        return isValidSubject(subject) ? Optional.of(new ExternalAccount(provider, subject)) : Optional.empty();
    }

    private static boolean isValidSubject(String subject) {
        return subject != null && SUBJECT.matcher(subject).matches();
    }

    @Override
    public String toString() {
        return "ExternalAccount[provider=" + provider + ", ***]";
    }
}
