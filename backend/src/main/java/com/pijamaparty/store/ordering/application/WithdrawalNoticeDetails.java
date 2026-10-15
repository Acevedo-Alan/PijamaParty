package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import java.util.Optional;
import java.util.UUID;

/** Outbound read port (UC-29, T-E3-25): what the emails of a withdrawal request tell, as the caller (the system). */
public interface WithdrawalNoticeDetails {

    /** Empty when there is no such request, or Row Level Security hides it from the actor. */
    Optional<WithdrawalNotice> noticeOf(UUID requestId);
}
