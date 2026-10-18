package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteReviewService implements DeleteReview {

    private final RemoveReview removeReview;

    DeleteReviewService(RemoveReview removeReview) {
        this.removeReview = removeReview;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return removeReview.remove(command.review()) ? new Result.Deleted() : new Result.NotFound();
    }
}
