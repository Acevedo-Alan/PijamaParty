package com.pijamaparty.store.backoffice.adapter.out.notifications;

import com.pijamaparty.store.backoffice.application.BuyerEmails;
import com.pijamaparty.store.backoffice.domain.OrderAlert.BuyerEmailProblem;
import com.pijamaparty.store.notifications.api.BuyerEmailStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Asks the public API of the notifications module about the buyer's email, in the caller's transaction. */
@Component
class NotificationsModuleBuyerEmails implements BuyerEmails {

    private final BuyerEmailStatus status;

    NotificationsModuleBuyerEmails(BuyerEmailStatus status) {
        this.status = status;
    }

    @Override
    public Optional<BuyerEmailProblem> problemOf(UUID orderId) {
        return status.problemOf(orderId).map(problem -> switch (problem) {
            case FAILED -> BuyerEmailProblem.FAILED;
            case BOUNCED -> BuyerEmailProblem.BOUNCED;
        });
    }
}
