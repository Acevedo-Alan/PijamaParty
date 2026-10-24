package com.pijamaparty.store.activity.application;

/**
 * D-E8-11 (T-E8-01): the daily job deletes the entries older than 2 years. Runs as {@code SYSTEM}, the only role Row
 * Level Security lets delete them, and only those (V41).
 */
public interface PurgeOldActivity {

    Result execute();

    record Result(int removed) {}
}
