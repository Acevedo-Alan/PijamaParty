package com.pijamaparty.store.activity.api;

/**
 * UC-37: records a back-office change. Called by the use case that made the change, after its own write succeeded and
 * inside its transaction (a call outside one fails), so the change and its entry commit together or not at all. A
 * call, not a domain event: an event would carry the changed values through {@code event_publication}, which has no
 * Row Level Security. A use case that changed nothing (a refused command, a replayed idempotent request) records
 * nothing.
 *
 * <p>Row Level Security lets the administrator record its own changes, and the system only the {@code ACCOUNT}
 * entries of signing in (V41).
 */
public interface ActivityLog {

    void record(ActivityEntry entry);
}
