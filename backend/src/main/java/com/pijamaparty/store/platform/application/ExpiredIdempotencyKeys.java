package com.pijamaparty.store.platform.application;

import java.time.Instant;

/** Outbound port (RNF-20.2): deletes the expired idempotency keys, within the caller's transaction. */
public interface ExpiredIdempotencyKeys {

    /** Deletes every key whose expiry is at or before {@code now}, and answers how many. */
    int deleteExpiredAt(Instant now);
}
