package com.pijamaparty.store.accounts.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** UC-04: a password has at least 12 characters and at most 72 bytes (the BCrypt limit, longer would be cut). */
public final class PasswordRules {

    public static final int MIN_CHARACTERS = 12;
    public static final int MAX_BYTES = 72;

    private PasswordRules() {
    }

    public static Optional<Violation> check(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            return Optional.of(Violation.TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of(Violation.TOO_LONG);
        }
        return Optional.empty();
    }

    public enum Violation {
        TOO_SHORT,
        TOO_LONG
    }
}
