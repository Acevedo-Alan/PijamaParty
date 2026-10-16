package com.pijamaparty.store.accounts.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A password has at most 72 bytes (the BCrypt limit, longer would be cut) and at least 12 characters for the
 * administrator (UC-04) or 8 for a customer (UC-19).
 */
public final class PasswordRules {

    public static final int MIN_CHARACTERS = 12;
    public static final int CUSTOMER_MIN_CHARACTERS = 8;
    public static final int MAX_BYTES = 72;

    private PasswordRules() {
    }

    /** The administrator's password (UC-04). */
    public static Optional<Violation> check(String password) {
        return check(password, MIN_CHARACTERS);
    }

    /** A customer's password (UC-19). */
    public static Optional<Violation> checkCustomer(String password) {
        return check(password, CUSTOMER_MIN_CHARACTERS);
    }

    private static Optional<Violation> check(String password, int minCharacters) {
        if (password == null || password.codePointCount(0, password.length()) < minCharacters) {
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
