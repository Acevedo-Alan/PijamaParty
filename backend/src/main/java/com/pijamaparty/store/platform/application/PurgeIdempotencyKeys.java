package com.pijamaparty.store.platform.application;

/**
 * T-E3-14 (RNF-20.2): the daily purge deletes the idempotency keys past their 24 hours. Runs as {@code SYSTEM}, the
 * only role Row Level Security lets reach every key (RNF-27).
 */
public interface PurgeIdempotencyKeys {

    Result execute();

    record Result(int removed) {}
}
