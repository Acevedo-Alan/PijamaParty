package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-37 (T-E8-03): what review moderation records in the activity log, and when it records nothing. */
class ReviewActivityServicesTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    @Test
    void givenAPendingReview_whenApprovedRejectedAndDeleted_thenEachEntryNamesItsProductAndNothingOfItsAuthor() {
        ReviewId review = reviewed();

        fixture.approve.execute(new ApproveReview.Command(fixture.admin, review));
        fixture.reject.execute(new RejectReview.Command(fixture.admin, review));
        fixture.deleteReview.execute(new DeleteReview.Command(fixture.admin, review));

        ActivitySubject subject = ActivitySubject.of(SubjectType.REVIEW, review.value(), "Pijama Luna");
        var admin = fixture.admin.userId().orElseThrow();
        assertThat(fixture.activity.entries()).containsExactly(
            new ActivityEntry(admin, ActivityAction.REVIEW_APPROVED, subject),
            new ActivityEntry(admin, ActivityAction.REVIEW_REJECTED, subject),
            new ActivityEntry(admin, ActivityAction.REVIEW_DELETED, subject));
    }

    @Test
    void givenADecidedReview_whenTheSameDecisionIsRepeatedOrAnUnknownOneIsDeleted_thenNothingMoreIsRecorded() {
        ReviewId review = reviewed();
        fixture.approve.execute(new ApproveReview.Command(fixture.admin, review));
        fixture.activity.clear();

        fixture.approve.execute(new ApproveReview.Command(fixture.admin, review));
        fixture.deleteReview.execute(new DeleteReview.Command(fixture.admin, ReviewId.of(UUID.randomUUID())));

        assertThat(fixture.activity.entries()).isEmpty();
    }

    @Test
    void givenAReviewOfAProductTheCatalogNoLongerHas_whenApproved_thenTheEntryHasNoLabel() {
        ReviewId review = ReviewId.of(UUID.randomUUID());
        fixture.reviews.save(Review.submit(review, ProductId.of(UUID.randomUUID()), fixture.anaId,
            new ReviewContent(4, Optional.empty()), new AuthorName("Ana"), ReviewsFixture.T0));

        fixture.approve.execute(new ApproveReview.Command(fixture.admin, review));

        assertThat(fixture.activity.single().subject())
            .isEqualTo(new ActivitySubject(SubjectType.REVIEW, Optional.of(review.value()), Optional.empty()));
    }

    private ReviewId reviewed() {
        fixture.submitted(fixture.ana, fixture.luna, 4, "Muy cómodo");
        return fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow().id();
    }
}
