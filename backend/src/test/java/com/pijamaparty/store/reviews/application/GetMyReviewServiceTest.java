package com.pijamaparty.store.reviews.application;

import static com.pijamaparty.store.reviews.application.ReviewsFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.GetMyReview.Query;
import com.pijamaparty.store.reviews.application.GetMyReview.Result;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-24 (T-E5-06): whether the customer may review a product, and its review of it. */
class GetMyReviewServiceTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    @Test
    void givenABuyerWithoutAReview_whenItOpensTheProduct_thenItMayReviewIt() {
        assertThat(fixture.get.execute(new Query(fixture.ana, fixture.luna)))
            .isEqualTo(new Result.Found(true, Optional.empty()));
    }

    @Test
    void givenACustomerWhoDidNotBuyTheProduct_whenItOpensIt_thenItMayNotReviewIt() {
        assertThat(fixture.get.execute(new Query(fixture.bruno, fixture.luna)))
            .isEqualTo(new Result.Found(false, Optional.empty()));
    }

    @Test
    void givenARejectedReview_whenItsAuthorOpensTheProduct_thenItSeesItWithItsStatus() {
        fixture.submitted(fixture.ana, fixture.luna, 2, "Destiñe");
        var stored = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow();
        fixture.reviews.moderate(stored.id(), ReviewStatus.REJECTED, T0.plusSeconds(60));

        assertThat(fixture.get.execute(new Query(fixture.ana, fixture.luna))).isEqualTo(new Result.Found(true,
            Optional.of(new MyReview(2, Optional.of("Destiñe"), ReviewStatus.REJECTED, T0))));
    }

    @Test
    void givenAnUnknownProduct_whenOpened_thenTheCustomerMayNotReviewIt() {
        assertThat(fixture.get.execute(new Query(fixture.ana, ProductId.of(UUID.randomUUID()))))
            .isEqualTo(new Result.Found(false, Optional.empty()));
    }

    @Test
    void givenAGuestOrTheAdministrator_whenAsking_thenItIsNotSignedIn() {
        for (Actor actor : new Actor[] {
            Actor.anonymous(Optional.empty(), Optional.empty()), Actor.admin(UserId.of(UUID.randomUUID()))}) {
            assertThat(fixture.get.execute(new Query(actor, fixture.luna))).isEqualTo(new Result.NotSignedIn());
        }
    }
}
