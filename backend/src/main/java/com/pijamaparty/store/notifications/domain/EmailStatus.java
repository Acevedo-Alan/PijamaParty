package com.pijamaparty.store.notifications.domain;

/** Where an email of the outbox stands (RNF-23). */
public enum EmailStatus {
    PENDING,
    SENT,
    /** Rejected, or out of attempts: it will not be sent. */
    FAILED,
    /** Sent, and the provider reports it never reached its recipient (T-E3-22). */
    BOUNCED
}
