package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.reviews.domain.Review;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ApproveReviewService implements ApproveReview {

    private final LockReview lockReview;
    private final SaveModeration saveModeration;
    private final ProductLinks productLinks;
    private final Clock clock;

    ApproveReviewService(LockReview lockReview, SaveModeration saveModeration, ProductLinks productLinks,
                         Clock clock) {
        this.lockReview = lockReview;
        this.saveModeration = saveModeration;
        this.productLinks = productLinks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Review> stored = lockReview.lockById(command.review());
        if (stored.isEmpty()) {
            return new Result.NotFound();
        }
        Review approved = stored.get().approved(clock.instant());
        if (!approved.equals(stored.get())) {
            saveModeration.saveDecision(approved);
        }
        return new Result.Approved(ModeratedReview.of(List.of(approved), productLinks).getFirst());
    }
}
