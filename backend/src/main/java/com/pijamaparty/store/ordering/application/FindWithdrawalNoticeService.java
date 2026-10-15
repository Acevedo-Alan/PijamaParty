package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.WithdrawalNotices;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-29: {@link WithdrawalNotices} for the emails of a withdrawal request, read as the caller (the email job). */
@Service
class FindWithdrawalNoticeService implements WithdrawalNotices {

    private final WithdrawalNoticeDetails details;

    FindWithdrawalNoticeService(WithdrawalNoticeDetails details) {
        this.details = details;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WithdrawalNotice> find(UUID requestId) {
        return details.noticeOf(requestId);
    }
}
