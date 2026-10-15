package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-29 (T-E3-26): the back office lists the withdrawal requests, newest first, 50 per page, only those in a status if
 * one is given.
 */
public interface ListWithdrawals {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /** {@code page} counts from 0. */
    record Query(Actor actor, Optional<WithdrawalStatus> status, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(status, "status");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        record Listed(List<WithdrawalSummary> withdrawals, boolean hasMore) implements Result {

            public Listed {
                withdrawals = List.copyOf(withdrawals);
            }
        }

        record Forbidden() implements Result {}
    }
}
