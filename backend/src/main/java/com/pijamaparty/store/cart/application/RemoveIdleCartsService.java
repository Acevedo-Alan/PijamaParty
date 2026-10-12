package com.pijamaparty.store.cart.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RemoveIdleCartsService implements RemoveIdleCarts {

    private final DeleteIdleCarts deleteIdleCarts;
    private final Clock clock;

    RemoveIdleCartsService(DeleteIdleCarts deleteIdleCarts, Clock clock) {
        this.deleteIdleCarts = deleteIdleCarts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute() {
        return new Result(deleteIdleCarts.deleteUpdatedBefore(clock.instant().minus(IDLE_PERIOD)));
    }
}
