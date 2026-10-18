package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.reviews.domain.Review;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListReviewsForModerationService implements ListReviewsForModeration {

    private final ReviewsForModeration reviews;
    private final ProductLinks productLinks;

    ListReviewsForModerationService(ReviewsForModeration reviews, ProductLinks productLinks) {
        this.reviews = reviews;
        this.productLinks = productLinks;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        List<Review> rows = reviews.withStatus(query.status(), Math.multiplyExact(query.page(), PAGE_SIZE),
            PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(ModeratedReview.of(hasMore ? rows.subList(0, PAGE_SIZE) : rows, productLinks),
            hasMore);
    }
}
