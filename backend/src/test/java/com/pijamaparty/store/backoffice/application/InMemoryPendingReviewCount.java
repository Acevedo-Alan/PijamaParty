package com.pijamaparty.store.backoffice.application;

/** Fake of {@link PendingReviewCount}: the pending reviews the test arranges. */
public class InMemoryPendingReviewCount implements PendingReviewCount {

    private long pending;

    /** Test arrangement: one more review waiting for moderation. */
    public synchronized void submitted() {
        pending++;
    }

    @Override
    public synchronized long pending() {
        return pending;
    }
}
