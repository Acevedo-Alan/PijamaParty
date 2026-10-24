package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.reviews.domain.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteReviewService implements DeleteReview {

    private final LockReview lockReview;
    private final RemoveReview removeReview;
    private final ProductLinks productLinks;
    private final ReviewActivity activity;

    DeleteReviewService(LockReview lockReview, RemoveReview removeReview, ProductLinks productLinks,
                        ReviewActivity activity) {
        this.lockReview = lockReview;
        this.removeReview = removeReview;
        this.productLinks = productLinks;
        this.activity = activity;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        // Read first: the entry names the review by its product, which it no longer tells once it is deleted.
        Optional<Review> stored = lockReview.lockById(command.review());
        if (stored.isEmpty() || !removeReview.remove(command.review())) {
            return new Result.NotFound();
        }
        activity.record(ReviewEntries.entry(command.actor(), ActivityAction.REVIEW_DELETED,
            ModeratedReview.of(List.of(stored.get()), productLinks).getFirst()));
        return new Result.Deleted();
    }
}
