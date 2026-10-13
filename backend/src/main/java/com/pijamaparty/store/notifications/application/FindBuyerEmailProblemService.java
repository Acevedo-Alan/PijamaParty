package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.api.BuyerEmailStatus;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-18: {@link BuyerEmailStatus} for the back-office alert, read as the caller (the administrator). */
@Service
class FindBuyerEmailProblemService implements BuyerEmailStatus {

    private final EmailStatuses statuses;

    FindBuyerEmailProblemService(EmailStatuses statuses) {
        this.statuses = statuses;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Problem> problemOf(UUID orderId) {
        return statuses.statusOf(EmailType.ORDER_PAID_CUSTOMER, orderId)
            .filter(status -> status == EmailStatus.FAILED)
            .map(failed -> Problem.FAILED);
    }
}
