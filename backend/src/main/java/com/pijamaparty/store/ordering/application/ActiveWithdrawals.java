package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.util.Optional;

/** Outbound read port (UC-29, T-E3-25): the active ({@code RECEIVED}) withdrawal request of an order. */
public interface ActiveWithdrawals {

    /** The code of the order's active request; empty when it has none (never had one, or all are resolved). */
    Optional<WithdrawalCode> codeOf(OrderId order);
}
