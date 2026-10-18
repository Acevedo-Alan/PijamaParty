package com.pijamaparty.store.reviews.application;

import static com.pijamaparty.store.reviews.application.ReviewsFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.ProductLinks.ProductLink;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-25 (T-E5-08): the administrator lists, approves, rejects and deletes reviews. */
class ReviewModerationServicesTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    /** A buyer of Pijama Luna named {@code name} submits a review of it, then a minute goes by. */
    private Review reviewed(String name, int rating) {
        UserId id = UserId.of(UUID.randomUUID());
        fixture.names.add(id, name);
        fixture.purchases.bought(id, fixture.lunaRosaM);
        fixture.submitted(ReviewsFixture.customer(id), fixture.luna, rating, null);
        fixture.clock.advance(Duration.ofMinutes(1));
        return fixture.reviews.findByAuthorAndProduct(id, fixture.luna).orElseThrow();
    }

    private List<ModeratedReview> listed(ReviewStatus status, int page) {
        var result = fixture.listForModeration.execute(new ListReviewsForModeration.Query(fixture.admin, status, page));
        return ((ListReviewsForModeration.Result.Listed) result).reviews();
    }

    private ModeratedReview approved(ReviewId id) {
        var result = fixture.approve.execute(new ApproveReview.Command(fixture.admin, id));
        return ((ApproveReview.Result.Approved) result).review();
    }

    private ModeratedReview rejected(ReviewId id) {
        var result = fixture.reject.execute(new RejectReview.Command(fixture.admin, id));
        return ((RejectReview.Result.Rejected) result).review();
    }

    private RatingSummary productPageSummary() {
        var result = fixture.listApproved.execute(new ListApprovedReviews.Query("pijama-luna", 0));
        return ((ListApprovedReviews.Result.Listed) result).summary();
    }

    // --- Acceptance criteria of UC-25 ---

    @Test
    void givenPendingReviews_whenTheAdministratorOpensThePanel_thenItSeesThemFromTheOldestToTheNewest() {
        Review older = reviewed("Ana Pérez", 5);
        Review newer = reviewed("Bruno Díaz", 3);
        approved(reviewed("Carla Gómez", 4).id());

        List<ModeratedReview> pending = listed(ReviewStatus.PENDING, 0);

        assertThat(pending).extracting(ModeratedReview::id).containsExactly(older.id(), newer.id());
        assertThat(pending.getFirst()).isEqualTo(new ModeratedReview(older.id(), fixture.luna,
            Optional.of(new ProductLink("Pijama Luna", "pijama-luna")), new AuthorName("Ana"),
            new ReviewContent(5, Optional.empty()), ReviewStatus.PENDING, T0));
    }

    @Test
    void givenARejectedReview_whenTheProductPageIsRead_thenItDoesNotAppearOrCountTowardTheAverage() {
        approved(reviewed("Ana Pérez", 4).id());
        Review bruno = reviewed("Bruno Díaz", 1);
        approved(bruno.id());

        rejected(bruno.id());

        assertThat(productPageSummary()).isEqualTo(new RatingSummary(1, 4));
    }

    // --- Business rules ---

    @Test
    void givenAPendingReview_whenApproved_thenItCountsTowardTheAverageAndRecordsWhen() {
        Review review = reviewed("Ana Pérez", 4);
        fixture.clock.advance(Duration.ofHours(1));

        ModeratedReview approved = approved(review.id());

        assertThat(approved.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(approved.updatedAt()).isEqualTo(review.updatedAt());
        assertThat(fixture.reviews.lockById(review.id()).orElseThrow().moderatedAt())
            .contains(fixture.clock.instant());
        assertThat(productPageSummary()).isEqualTo(new RatingSummary(1, 4));
    }

    @Test
    void givenADecidedReview_whenTheSameDecisionIsRepeated_thenNothingChanges() {
        Review review = reviewed("Ana Pérez", 4);
        approved(review.id());
        Review afterFirst = fixture.reviews.lockById(review.id()).orElseThrow();
        fixture.clock.advance(Duration.ofHours(1));

        assertThat(approved(review.id()).status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(fixture.reviews.lockById(review.id())).contains(afterFirst);

        rejected(review.id());
        Review afterReject = fixture.reviews.lockById(review.id()).orElseThrow();
        fixture.clock.advance(Duration.ofHours(1));
        rejected(review.id());
        assertThat(fixture.reviews.lockById(review.id())).contains(afterReject);
    }

    @Test
    void givenARejectedReview_whenApproved_thenItShowsAgain() {
        Review review = reviewed("Ana Pérez", 5);
        rejected(review.id());

        approved(review.id());

        assertThat(productPageSummary()).isEqualTo(new RatingSummary(1, 5));
        assertThat(listed(ReviewStatus.REJECTED, 0)).isEmpty();
        assertThat(listed(ReviewStatus.APPROVED, 0)).extracting(ModeratedReview::id).containsExactly(review.id());
    }

    @Test
    void givenAnApprovedReview_whenItsAuthorEditsIt_thenItIsPendingAgain() {
        fixture.submitted(fixture.ana, fixture.luna, 5, null);
        Review review = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow();
        approved(review.id());
        fixture.clock.advance(Duration.ofMinutes(1));

        fixture.submitted(fixture.ana, fixture.luna, 2, "Destiñe");

        assertThat(listed(ReviewStatus.PENDING, 0)).extracting(ModeratedReview::id).containsExactly(review.id());
        assertThat(productPageSummary()).isEqualTo(RatingSummary.NONE);
    }

    @Test
    void givenAReview_whenDeleted_thenItIsGoneAndItsAuthorCanWriteANewOne() {
        fixture.submitted(fixture.ana, fixture.luna, 5, "Muy cómodo");
        ReviewId id = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow().id();

        assertThat(fixture.deleteReview.execute(new DeleteReview.Command(fixture.admin, id)))
            .isEqualTo(new DeleteReview.Result.Deleted());
        assertThat(fixture.deleteReview.execute(new DeleteReview.Command(fixture.admin, id)))
            .isEqualTo(new DeleteReview.Result.NotFound());
        assertThat(fixture.reviews.all()).isEmpty();

        assertThat(fixture.submitted(fixture.ana, fixture.luna, 3, null)).isInstanceOf(SubmitReview.Result.Created.class);
    }

    @Test
    void givenAnUnknownReview_whenApprovedOrRejected_thenItIsNotFound() {
        ReviewId unknown = ReviewId.of(UUID.randomUUID());

        assertThat(fixture.approve.execute(new ApproveReview.Command(fixture.admin, unknown)))
            .isEqualTo(new ApproveReview.Result.NotFound());
        assertThat(fixture.reject.execute(new RejectReview.Command(fixture.admin, unknown)))
            .isEqualTo(new RejectReview.Result.NotFound());
    }

    @Test
    void givenAReviewOfAProductTheCatalogNoLongerHas_whenListed_thenItComesWithoutItsLink() {
        ProductId gone = ProductId.of(UUID.randomUUID());
        Review review = Review.submit(ReviewId.of(UUID.randomUUID()), gone, UserId.of(UUID.randomUUID()),
            new ReviewContent(4, Optional.empty()), new AuthorName("Ana"), T0);
        fixture.reviews.save(review);

        assertThat(listed(ReviewStatus.PENDING, 0)).singleElement()
            .satisfies(moderated -> assertThat(moderated.productLink()).isEmpty());
    }

    @Test
    void givenMoreThanAPageOfPendingReviews_whenListed_thenTheyComeFiftyAtATime() {
        IntStream.range(0, ListReviewsForModeration.PAGE_SIZE + 1).forEach(i -> reviewed("Cliente " + i, 4));

        var first = (ListReviewsForModeration.Result.Listed) fixture.listForModeration.execute(
            new ListReviewsForModeration.Query(fixture.admin, ReviewStatus.PENDING, 0));
        var second = (ListReviewsForModeration.Result.Listed) fixture.listForModeration.execute(
            new ListReviewsForModeration.Query(fixture.admin, ReviewStatus.PENDING, 1));

        assertThat(first.reviews()).hasSize(ListReviewsForModeration.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.reviews()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.reviews().getFirst().updatedAt()).isAfter(first.reviews().getLast().updatedAt());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenModerating_thenItIsForbiddenAndNothingChanges() {
        Review review = reviewed("Ana Pérez", 4);

        for (Actor actor : List.of(fixture.ana, Actor.anonymous(Optional.empty(), Optional.empty()),
            Actor.system())) {
            assertThat(fixture.listForModeration.execute(new ListReviewsForModeration.Query(actor,
                ReviewStatus.PENDING, 0))).isEqualTo(new ListReviewsForModeration.Result.Forbidden());
            assertThat(fixture.approve.execute(new ApproveReview.Command(actor, review.id())))
                .isEqualTo(new ApproveReview.Result.Forbidden());
            assertThat(fixture.reject.execute(new RejectReview.Command(actor, review.id())))
                .isEqualTo(new RejectReview.Result.Forbidden());
            assertThat(fixture.deleteReview.execute(new DeleteReview.Command(actor, review.id())))
                .isEqualTo(new DeleteReview.Result.Forbidden());
        }
        assertThat(fixture.reviews.all()).containsExactly(review);
    }
}
