package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Sku;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link SkuGenerator} for tests: PP-T0000001, then PP-T0000002, and so on, after any SKUs queued with
 * {@link #thenReturn}, so a test can force a clash.
 */
public final class InMemorySkuGenerator implements SkuGenerator {

    private final Deque<String> queued = new ArrayDeque<>();
    private final AtomicLong last = new AtomicLong();

    /** The SKU that the n-th generated call (starting at 1, queued ones not counted) returns. */
    public static Sku nth(long n) {
        return new Sku("PP-T%07d".formatted(n));
    }

    public synchronized InMemorySkuGenerator thenReturn(String sku) {
        queued.add(sku);
        return this;
    }

    @Override
    public synchronized Sku newSku() {
        String next = queued.poll();
        return next != null ? new Sku(next) : nth(last.incrementAndGet());
    }
}
