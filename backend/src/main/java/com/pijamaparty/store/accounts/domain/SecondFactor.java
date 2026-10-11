package com.pijamaparty.store.accounts.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * The administrator's authenticator app (T-E1-16): its encrypted secret, when it was enrolled and the time step of
 * the last code accepted, so a code is never accepted twice.
 */
public record SecondFactor(EncryptedSecret secret, Instant enabledAt, long lastStep) {

    public SecondFactor {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(enabledAt, "enabledAt");
        if (lastStep < 0) {
            throw new IllegalArgumentException("lastStep must not be negative");
        }
    }

    SecondFactor afterStep(long step) {
        return new SecondFactor(secret, enabledAt, step);
    }
}
