package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;

/**
 * UC-37: the activity entries of review moderation, labelled with the name of the product reviewed, never with the
 * review's text, rating or author (T-E8-03).
 */
final class ReviewEntries {

    private ReviewEntries() {
    }

    static ActivityEntry entry(Actor administrator, ActivityAction action, ModeratedReview review) {
        return new ActivityEntry(administrator.userId().orElseThrow(
            () -> new IllegalArgumentException("only an administrator moderates reviews")), action,
            new ActivitySubject(SubjectType.REVIEW, Optional.of(review.id().value()),
                review.productLink().map(ProductLinks.ProductLink::name)));
    }
}
