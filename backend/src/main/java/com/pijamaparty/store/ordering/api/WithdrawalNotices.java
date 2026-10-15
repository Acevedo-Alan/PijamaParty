package com.pijamaparty.store.ordering.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-29 (T-E3-25): what the emails of a withdrawal request tell. Read in its own transaction, or the caller's, as the
 * caller: the system, when the email job composes them.
 */
public interface WithdrawalNotices {

    /** Empty when there is no such request, or Row Level Security hides it from the actor. */
    Optional<WithdrawalNotice> find(UUID requestId);

    /**
     * Personal data (R-05): {@link #toString()} prints only the order number and the code.
     *
     * @param buyerEmail the order's email now, where the code goes
     */
    record WithdrawalNotice(UUID requestId, String code, long orderNumber, String buyerName, String buyerEmail,
                            boolean outOfTerm) {

        public WithdrawalNotice {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(buyerName, "buyerName");
            Objects.requireNonNull(buyerEmail, "buyerEmail");
        }

        @Override
        public String toString() {
            return "WithdrawalNotice[orderNumber=" + orderNumber + ", code=" + code + ", ***]";
        }
    }
}
