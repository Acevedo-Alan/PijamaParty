package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link ApprovedReviews} (UC-24, UC-25, T-E5-07), arranged through {@link SaveReview} on the same storage.
 * Each review has an author of its own, as there is one review per customer and product.
 */
public abstract class ApprovedReviewsContractTest<T extends SaveReview & ApprovedReviews> extends ContractTest<T> {

    /** Whole microseconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    protected static final Instant T0 = Instant.parse("2026-10-18T12:00:00.123456Z");

    private final ProductId luna = ProductId.of(UUID.randomUUID());
    private final ProductId sol = ProductId.of(UUID.randomUUID());

    /** The administrator decided on the stored review (UC-25). */
    protected abstract void moderated(ReviewId review, ReviewStatus status, Instant at);

    /** Stores a review of a new author, and the administrator's decision on it unless it stays pending. */
    private Review given(ProductId product, int rating, String text, String author, ReviewStatus status,
                         Instant createdAt) {
        Review review = Review.submit(ReviewId.of(UUID.randomUUID()), product, UserId.of(UUID.randomUUID()),
            new ReviewContent(rating, Optional.ofNullable(text)), new AuthorName(author), createdAt);
        subject.save(review);
        if (status != ReviewStatus.PENDING) {
            moderated(review.id(), status, createdAt.plusSeconds(60));
        }
        return review;
    }

    private static PublishedReview published(int rating, String text, String author, Instant createdAt) {
        return new PublishedReview(new AuthorName(author), new ReviewContent(rating, Optional.ofNullable(text)),
            createdAt);
    }

    @Test
    void givenNoReviews_whenReadingTheProductPage_thenThereAreNone() {
        assertThat(subject.summaryOf(luna)).isEqualTo(RatingSummary.NONE);
        assertThat(subject.newestOf(luna, 0, 10)).isEmpty();
    }

    @Test
    void givenReviewsInEveryStatus_whenReadingTheProductPage_thenOnlyTheApprovedOnesOfTheProductCountAndShow() {
        given(luna, 4, "Lindo", "Ana", ReviewStatus.APPROVED, T0);
        given(luna, 5, null, "Bruno", ReviewStatus.APPROVED, T0.plusSeconds(1));
        given(luna, 1, "Pendiente", "Carla", ReviewStatus.PENDING, T0.plusSeconds(2));
        given(luna, 2, "Rechazada", "Darío", ReviewStatus.REJECTED, T0.plusSeconds(3));
        given(sol, 3, null, "Eva", ReviewStatus.APPROVED, T0.plusSeconds(4));
        persistAndForget();

        assertThat(subject.summaryOf(luna)).isEqualTo(new RatingSummary(2, 9));
        assertThat(subject.newestOf(luna, 0, 10)).containsExactly(
            published(5, null, "Bruno", T0.plusSeconds(1)),
            published(4, "Lindo", "Ana", T0));
    }

    @Test
    void givenManyApprovedReviews_whenReadingAPage_thenItSkipsTheNewerOnesAndStopsAtTheLimit() {
        IntStream.range(0, 5).forEach(i -> given(luna, 5, "Opinión " + i, "Ana", ReviewStatus.APPROVED,
            T0.plusSeconds(i)));
        persistAndForget();

        assertThat(subject.newestOf(luna, 2, 2)).extracting(review -> review.content().text().orElseThrow())
            .containsExactly("Opinión 2", "Opinión 1");
        assertThat(subject.newestOf(luna, 4, 2)).hasSize(1);
        assertThat(subject.newestOf(luna, 5, 2)).isEmpty();
    }

    @Test
    void givenAnApprovedReview_whenItsAuthorEditsIt_thenItLeavesTheProductPageAndTheAverage() {
        Review approved = given(luna, 5, "Muy cómodo", "Ana", ReviewStatus.APPROVED, T0);

        subject.save(approved.resubmit(new ReviewContent(1, Optional.empty()), new AuthorName("Ana"),
            T0.plusSeconds(3_600)));
        persistAndForget();

        assertThat(subject.summaryOf(luna)).isEqualTo(RatingSummary.NONE);
        assertThat(subject.newestOf(luna, 0, 10)).isEmpty();
    }
}
