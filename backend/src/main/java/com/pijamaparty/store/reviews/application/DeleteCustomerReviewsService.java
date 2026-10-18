package com.pijamaparty.store.reviews.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins the account deletion's transaction, as the customer itself, which deletes its own reviews once its account is
 * gone (V37).
 */
@Service
class DeleteCustomerReviewsService implements DeleteCustomerReviews {

    private final RemoveCustomerReviews removeReviews;

    DeleteCustomerReviewsService(RemoveCustomerReviews removeReviews) {
        this.removeReviews = removeReviews;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        int deleted = removeReviews.removeByAuthor(command.customer());
        return deleted == 0 ? new Result.NoReviews() : new Result.Deleted(deleted);
    }
}
