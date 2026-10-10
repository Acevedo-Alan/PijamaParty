package com.pijamaparty.store.platform.domain;

/** Who is acting. Same values as the Row Level Security context {@code app.role} (RNF-27). */
public enum Role {
    /** Visitor or guest, identified at most by a cart token or an order tracking token. */
    ANONYMOUS,
    CUSTOMER,
    ADMIN,
    /** Scheduled jobs, webhooks, email sending and authentication. */
    SYSTEM
}
