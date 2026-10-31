package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.api.BuyerEmailStatus;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
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
            .flatMap(status -> switch (status) {
                case PENDING, SENT -> Optional.empty();
                case FAILED -> Optional.of(Problem.FAILED);
                case BOUNCED -> Optional.of(Problem.BOUNCED);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> ordersWithProblems() {
        return statuses.referencesWithLatestIn(EmailType.ORDER_PAID_CUSTOMER,
            EnumSet.of(EmailStatus.FAILED, EmailStatus.BOUNCED));
    }
}
