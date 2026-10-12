package com.pijamaparty.store.platform.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link TokenGenerator} for tests: the n-th call returns n left-padded with 'A' to
 * {@value TokenGenerator#LENGTH} characters, so assertions can name the tokens a test creates.
 */
public final class InMemoryTokenGenerator implements TokenGenerator {

    private final AtomicLong last = new AtomicLong();

    /** The token that the n-th call (starting at 1) returns. */
    public static String nth(long n) {
        String digits = Long.toString(n);
        return "A".repeat(LENGTH - digits.length()) + digits;
    }

    @Override
    public String newToken() {
        return nth(last.incrementAndGet());
    }
}
