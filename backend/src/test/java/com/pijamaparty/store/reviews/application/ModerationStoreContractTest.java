package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link ReviewsForModeration}, {@link LockReview}, {@link SaveModeration} and {@link RemoveReview} over
 * the same storage (UC-25), as the administrator uses them.
 */
public abstract class ModerationStoreContractTest<
    T extends ReviewsForModeration & LockReview & SaveModeration & RemoveReview> extends ContractTest<T> {

    /** Whole microseconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    protected static final Instant T0 = Instant.parse("2026-10-18T12:00:00.123456Z");

    private final ProductId luna = ProductId.of(UUID.randomUUID());

    /** The review is stored as its author left it, with the status and moderation time it has. */
    protected abstract void stored(Review review);

    /** A review of a new author, last submitted {@code minutes} after T0, in {@code status}. */
    private Review review(int minutes, ReviewStatus status) {
        Instant submitted = T0.plusSeconds(60L * minutes);
        Review pending = Review.submit(ReviewId.of(UUID.randomUUID()), luna, UserId.of(UUID.randomUUID()),
            new ReviewContent(4, Optional.of("Lindo")), new AuthorName("Ana"), submitted);
        Review review = switch (status) {
            case PENDING -> pending;
            case APPROVED -> pending.approved(submitted.plusSeconds(1));
            case REJECTED -> pending.rejected(submitted.plusSeconds(1));
        };
        stored(review);
        return review;
    }

    @Test
    void givenReviewsInEveryStatus_whenListingThePendingOnes_thenOnlyTheyComeFromTheOldestSubmission() {
        Review newer = review(10, ReviewStatus.PENDING);
        Review older = review(5, ReviewStatus.PENDING);
        review(1, ReviewStatus.APPROVED);
        review(2, ReviewStatus.REJECTED);
        persistAndForget();

        assertThat(subject.withStatus(ReviewStatus.PENDING, 0, 10)).containsExactly(older, newer);
    }

    @Test
    void givenDecidedReviews_whenListingOneStatus_thenOnlyItsReviewsComeNewestFirst() {
        Review olderApproved = review(1, ReviewStatus.APPROVED);
        Review newerApproved = review(7, ReviewStatus.APPROVED);
        Review rejected = review(3, ReviewStatus.REJECTED);
        review(5, ReviewStatus.PENDING);
        persistAndForget();

        assertThat(subject.withStatus(ReviewStatus.APPROVED, 0, 10)).containsExactly(newerApproved, olderApproved);
        assertThat(subject.withStatus(ReviewStatus.REJECTED, 0, 10)).containsExactly(rejected);
    }

    @Test
    void givenThreePendingReviews_whenListingAPage_thenTheOffsetAndLimitApply() {
        review(1, ReviewStatus.PENDING);
        Review second = review(2, ReviewStatus.PENDING);
        review(3, ReviewStatus.PENDING);
        persistAndForget();

        assertThat(subject.withStatus(ReviewStatus.PENDING, 1, 1)).containsExactly(second);
        assertThat(subject.withStatus(ReviewStatus.PENDING, 3, 10)).isEmpty();
    }

    @Test
    void givenAStoredReview_whenLockedById_thenItComesAsStoredAndAnUnknownOneDoesNot() {
        Review review = review(1, ReviewStatus.APPROVED);
        persistAndForget();

        assertThat(subject.lockById(review.id())).contains(review);
        assertThat(subject.lockById(ReviewId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenAPendingReview_whenADecisionIsSaved_thenOnlyItsStatusAndModerationTimeChange() {
        Review pending = review(1, ReviewStatus.PENDING);
        Review approved = pending.approved(T0.plusSeconds(3_600));
        // Fields other than the decision are never written, whatever the review passed carries.
        Review carryingOtherContent = new Review(approved.id(), approved.product(), approved.author(),
            new ReviewContent(1, Optional.empty()), new AuthorName("Otro"), approved.status(), approved.createdAt(),
            approved.updatedAt(), approved.moderatedAt());

        subject.saveDecision(carryingOtherContent);
        persistAndForget();

        assertThat(subject.lockById(pending.id())).contains(approved);
        assertThat(subject.withStatus(ReviewStatus.PENDING, 0, 10)).isEmpty();
    }

    @Test
    void givenTwoReviews_whenOneIsRemoved_thenOnlyItGoesAndRemovingItAgainFindsNone() {
        Review removed = review(1, ReviewStatus.PENDING);
        Review kept = review(2, ReviewStatus.PENDING);

        assertThat(subject.remove(removed.id())).isTrue();
        assertThat(subject.remove(removed.id())).isFalse();
        persistAndForget();

        assertThat(subject.lockById(removed.id())).isEmpty();
        assertThat(subject.withStatus(ReviewStatus.PENDING, 0, 10)).containsExactly(kept);
    }
}
