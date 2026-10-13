package com.pijamaparty.store.notifications.domain;

/** Where an email of the outbox stands (RNF-23); {@code BOUNCED} arrives with T-E3-22. */
public enum EmailStatus {
    PENDING,
    SENT,
    /** Rejected, or out of attempts: it will not be sent. */
    FAILED
}
