package com.pijamaparty.store.reviews.application;

import static com.pijamaparty.store.reviews.application.ReviewsFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.ListApprovedReviews.Query;
import com.pijamaparty.store.reviews.application.ListApprovedReviews.Result;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-24 (T-E5-07): the approved reviews of a product page, with their average and count. */
class ListApprovedReviewsServiceTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    /** A buyer of Pijama Luna named {@code name} submits a review of it, approved or left as is. */
    private void reviewed(String name, int rating, String text, ReviewStatus decision) {
        UserId id = UserId.of(UUID.randomUUID());
        fixture.names.add(id, name);
        fixture.purchases.bought(id, fixture.lunaRosaM);
        Actor buyer = ReviewsFixture.customer(id);
        fixture.submitted(buyer, fixture.luna, rating, text);
        if (decision != ReviewStatus.PENDING) {
            var stored = fixture.reviews.findByAuthorAndProduct(id, fixture.luna).orElseThrow();
            fixture.reviews.moderate(stored.id(), decision, fixture.clock.instant());
        }
        fixture.clock.advance(Duration.ofMinutes(1));
    }

    private Result.Listed listed(int page) {
        return (Result.Listed) fixture.listApproved.execute(new Query("pijama-luna", page));
    }

    @Test
    void givenApprovedReviewsOf4And5_whenTheProductPageIsOpened_thenTheAverageIs4Point5WithTheCount() {
        reviewed("Ana María Pérez", 4, "Lindo", ReviewStatus.APPROVED);
        reviewed("Bruno Díaz", 5, null, ReviewStatus.APPROVED);

        Result.Listed listed = listed(0);

        assertThat(listed.summary()).isEqualTo(new RatingSummary(2, 9));
        assertThat(listed.summary().average()).contains(new BigDecimal("4.5"));
        assertThat(listed.reviews()).containsExactly(
            new PublishedReview(new AuthorName("Bruno"), new ReviewContent(5, Optional.empty()), T0.plusSeconds(60)),
            new PublishedReview(new AuthorName("Ana"), new ReviewContent(4, Optional.of("Lindo")), T0));
    }

    @Test
    void givenAReviewJustSubmittedAndARejectedOne_whenTheProductPageIsOpened_thenNeitherShowsNorCounts() {
        reviewed("Ana Pérez", 5, "Muy cómodo", ReviewStatus.PENDING);
        reviewed("Bruno Díaz", 1, "Feo", ReviewStatus.REJECTED);

        assertThat(listed(0)).isEqualTo(new Result.Listed(RatingSummary.NONE, List.of()));
    }

    @Test
    void givenMoreApprovedReviewsThanAPage_whenTheSecondPageIsRead_thenItHasTheOlderOnesAndTheSameCount() {
        for (int i = 0; i < ListApprovedReviews.PAGE_SIZE + 2; i++) {
            reviewed("Cliente" + i, 5, "Opinión " + i, ReviewStatus.APPROVED);
        }

        Result.Listed second = listed(1);

        assertThat(second.summary().count()).isEqualTo(ListApprovedReviews.PAGE_SIZE + 2);
        assertThat(second.reviews()).extracting(review -> review.content().text().orElseThrow())
            .containsExactly("Opinión 1", "Opinión 0");
        assertThat(listed(2).reviews()).isEmpty();
    }

    @Test
    void givenASlugVisitorsDoNotSee_whenItsReviewsAreRead_thenTheProductIsNotFound() {
        assertThat(fixture.listApproved.execute(new Query("pijama-nube", 0))).isEqualTo(new Result.ProductNotFound());
    }
}
