package com.pijamaparty.store.reviews.adapter.in.event;

import com.pijamaparty.store.accounts.api.CustomerAccountDeleted;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.DeleteCustomerReviews;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * UC-28 (T-E5-06): a deleted customer's reviews go with its account. A plain (synchronous) listener, so it runs in the
 * deletion's transaction, as that customer, and commits or rolls back with it. Idempotent: a second run finds no
 * review.
 */
@Component
class DeletedAccountReviewsListener {

    private final DeleteCustomerReviews deleteCustomerReviews;

    DeletedAccountReviewsListener(DeleteCustomerReviews deleteCustomerReviews) {
        this.deleteCustomerReviews = deleteCustomerReviews;
    }

    @EventListener
    void on(CustomerAccountDeleted event) {
        deleteCustomerReviews.execute(new DeleteCustomerReviews.Command(UserId.of(event.userId())));
    }
}
