package com.pijamaparty.store.accounts.application;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Fake of {@link ExternalSignIns}: runs the block, one at a time, as the real adapter's transaction would. It cannot
 * roll back; the use-case tests that need it check what the real adapter does instead.
 */
public class InMemoryExternalSignIns implements ExternalSignIns {

    private final AtomicInteger transactions = new AtomicInteger();

    @Override
    public synchronized <R> R signingIn(Supplier<R> signIn) {
        transactions.incrementAndGet();
        return signIn.get();
    }

    /** How many transactions ran. */
    public int transactions() {
        return transactions.get();
    }
}
