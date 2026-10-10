package com.pijamaparty.store.accounts.domain;

import java.util.Objects;

/** A stored password hash, with its algorithm prefix (for example {@code {bcrypt}}). Never printed. */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("empty password hash");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
