package com.pijamaparty.store.accounts.application;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Fake of {@link CustomerAccountChanges}: runs the block, one at a time, as the real adapter's transaction would. */
public class InMemoryCustomerAccountChanges implements CustomerAccountChanges {

    private final AtomicInteger transactions = new AtomicInteger();

    @Override
    public synchronized <R> R changing(Supplier<R> change) {
        transactions.incrementAndGet();
        return change.get();
    }

    /** How many transactions ran. */
    public int transactions() {
        return transactions.get();
    }
}
