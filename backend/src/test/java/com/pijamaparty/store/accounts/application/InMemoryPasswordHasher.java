package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordHash;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake of {@link PasswordHasher}: fast and deterministic (tests only), and counts checks to verify the decoy. */
public class InMemoryPasswordHasher implements PasswordHasher {

    private static final String PREFIX = "{plain}";
    private static final PasswordHash DECOY = new PasswordHash(PREFIX + "\u0000decoy");

    private final AtomicInteger checks = new AtomicInteger();

    @Override
    public PasswordHash hash(String password) {
        return new PasswordHash(PREFIX + Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public boolean matches(String password, PasswordHash hash) {
        checks.incrementAndGet();
        return hash.equals(hash(password)) && !hash.equals(DECOY);
    }

    @Override
    public PasswordHash decoy() {
        return DECOY;
    }

    public int checks() {
        return checks.get();
    }
}
