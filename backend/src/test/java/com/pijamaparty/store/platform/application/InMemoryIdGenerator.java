package com.pijamaparty.store.platform.application;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link IdGenerator} for tests: 00000000-0000-0000-0000-000000000001, then ...0002, and so on, so
 * assertions can name the ids a use case creates.
 */
public final class InMemoryIdGenerator implements IdGenerator {

    private final AtomicLong last = new AtomicLong();

    /** The id that the n-th call (starting at 1) returns. */
    public static UUID nth(long n) {
        return new UUID(0, n);
    }

    @Override
    public UUID newId() {
        return nth(last.incrementAndGet());
    }
}
