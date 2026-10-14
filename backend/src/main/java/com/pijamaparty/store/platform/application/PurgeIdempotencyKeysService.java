package com.pijamaparty.store.platform.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurgeIdempotencyKeysService implements PurgeIdempotencyKeys {

    private final ExpiredIdempotencyKeys expiredKeys;
    private final Clock clock;

    PurgeIdempotencyKeysService(ExpiredIdempotencyKeys expiredKeys, Clock clock) {
        this.expiredKeys = expiredKeys;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute() {
        return new Result(expiredKeys.deleteExpiredAt(clock.instant()));
    }
}
