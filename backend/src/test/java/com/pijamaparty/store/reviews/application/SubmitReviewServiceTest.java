package com.pijamaparty.store.reviews.application;

import static com.pijamaparty.store.reviews.application.ReviewsFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.SubmitReview.Result;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewForm;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-24 (T-E5-06): a customer reviews a product it bought, or edits its review. */
class SubmitReviewServiceTest {

    private final ReviewsFixture fixture = new ReviewsFixture();

    @Test
    void givenACustomerWhoBoughtTheProduct_whenItReviewsIt_thenTheReviewWaitsForModerationUnderItsFirstName() {
        Result result = fixture.submitted(fixture.ana, fixture.luna, 5, "  Muy cómodo ");

        assertThat(result).isEqualTo(new Result.Created(
            new MyReview(5, Optional.of("Muy cómodo"), ReviewStatus.PENDING, T0)));
        Review stored = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow();
        assertThat(stored.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(stored.authorName()).isEqualTo(new AuthorName("Ana"));
    }

    @Test
    void givenACustomerWhoDidNotBuyTheProduct_whenItTriesToReviewIt_thenItIsToldOnlyBuyersCanAndNothingIsStored() {
        assertThat(fixture.submitted(fixture.bruno, fixture.luna, 4, null)).isEqualTo(new Result.NotABuyer());
        assertThat(fixture.reviews.all()).isEmpty();
    }

    @Test
    void givenAnApprovedReview_whenItsAuthorEditsIt_thenItIsReplacedAndGoesBackToModeration() {
        fixture.submitted(fixture.ana, fixture.luna, 5, "Muy cómodo");
        Review first = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow();
        fixture.reviews.moderate(first.id(), ReviewStatus.APPROVED, T0.plusSeconds(60));
        fixture.clock.advance(Duration.ofDays(2));
        fixture.names.add(fixture.anaId, "Anita Pérez");

        Result result = fixture.submitted(fixture.ana, fixture.luna, 3, "  ");

        assertThat(result).isEqualTo(new Result.Replaced(
            new MyReview(3, Optional.empty(), ReviewStatus.PENDING, T0.plus(Duration.ofDays(2)))));
        Review edited = fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow();
        assertThat(edited.id()).isEqualTo(first.id());
        assertThat(edited.createdAt()).isEqualTo(T0);
        assertThat(edited.moderatedAt()).isEmpty();
        assertThat(edited.authorName()).as("the name when last submitted").isEqualTo(new AuthorName("Anita"));
        assertThat(fixture.reviews.all()).hasSize(1);
    }

    @Test
    void givenAReviewWhoseOrderWasCancelledLater_whenItsAuthorEditsIt_thenItIsRefusedAndTheReviewStays() {
        fixture.submitted(fixture.ana, fixture.luna, 5, "Muy cómodo");
        fixture.purchases.cancelAll(fixture.anaId);

        assertThat(fixture.submitted(fixture.ana, fixture.luna, 1, "Cambié de idea"))
            .isEqualTo(new Result.NotABuyer());
        assertThat(fixture.reviews.findByAuthorAndProduct(fixture.anaId, fixture.luna).orElseThrow()
            .content().rating()).isEqualTo(5);
    }

    @Test
    void givenFieldsThatBreakTheRules_whenSubmitted_thenTheyAreNamedAndNothingIsStored() {
        var command = new SubmitReview.Command(fixture.ana, fixture.luna,
            new ReviewForm(new BigDecimal("4.5"), "x".repeat(1_001)));

        assertThat(fixture.submit.execute(command))
            .isEqualTo(new Result.InvalidReview(Set.of(ReviewForm.Field.RATING, ReviewForm.Field.TEXT)));
        assertThat(fixture.reviews.all()).isEmpty();
    }

    @Test
    void givenAnUnknownProduct_whenReviewed_thenItIsNotFound() {
        assertThat(fixture.submitted(fixture.ana, ProductId.of(UUID.randomUUID()), 5, null))
            .isEqualTo(new Result.ProductNotFound());
    }

    @Test
    void givenAGuestTheAdministratorOrADeletedAccount_whenReviewing_thenItIsNotSignedIn() {
        UserId deleted = UserId.of(UUID.randomUUID());
        fixture.purchases.bought(deleted, fixture.lunaRosaM);
        for (Actor actor : new Actor[] {
            Actor.anonymous(Optional.empty(), Optional.empty()),
            Actor.admin(UserId.of(UUID.randomUUID())),
            ReviewsFixture.customer(deleted)}) {
            assertThat(fixture.submitted(actor, fixture.luna, 5, null)).as(actor.toString())
                .isEqualTo(new Result.NotSignedIn());
        }
        assertThat(fixture.reviews.all()).isEmpty();
    }
}
