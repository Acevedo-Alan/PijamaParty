package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import java.util.List;
import java.util.Optional;

/** Outbound read port (UC-29, T-E3-26): the back-office list of withdrawal requests, within the caller's transaction. */
public interface WithdrawalPages {

    /** Newest first (then by code), only those in {@code status} when it is present. */
    List<WithdrawalSummary> page(Optional<WithdrawalStatus> status, int offset, int limit);
}
