package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
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
 * Contract of {@link LoadReview}, {@link SaveReview}, {@link CustomerReviews} and {@link RemoveCustomerReviews} over
 * the same storage (UC-24, UC-28). The authors are the customers of {@link RlsPolicyTester}, so a real adapter can act
 * as each of them (Row Level Security, RNF-27).
 */
public abstract class ReviewStoreContractTest<
    T extends LoadReview & SaveReview & CustomerReviews & RemoveCustomerReviews> extends ContractTest<T> {

    protected static final UserId ANA = RlsPolicyTester.CUSTOMER_A_ID;
    protected static final UserId BRUNO = RlsPolicyTester.CUSTOMER_B_ID;

    /** Whole microseconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    protected static final Instant T0 = Instant.parse("2026-10-18T12:00:00.123456Z");

    private final ProductId luna = ProductId.of(UUID.randomUUID());
    private final ProductId sol = ProductId.of(UUID.randomUUID());

    /** The administrator decided on the stored review (UC-25). */
    protected abstract void moderated(ReviewId review, ReviewStatus status, Instant at);

    protected static Review review(UserId author, ProductId product, int rating, Optional<String> text,
                                   Instant at) {
        return Review.submit(ReviewId.of(UUID.randomUUID()), product, author, new ReviewContent(rating, text),
            new AuthorName("Ana"), at);
    }

    @Test
    void givenNoReview_whenReadingTheAuthorsReviewOfTheProduct_thenThereIsNone() {
        assertThat(subject.findByAuthorAndProduct(ANA, luna)).isEmpty();
        assertThat(subject.byAuthor(ANA)).isEmpty();
    }

    @Test
    void givenAFirstReview_whenSaved_thenItIsCreatedAndReadBackAsIs() {
        Review review = review(ANA, luna, 5, Optional.of("Muy cómodo"), T0);

        assertThat(subject.save(review)).isEqualTo(SaveReview.Outcome.CREATED);
        persistAndForget();

        assertThat(subject.findByAuthorAndProduct(ANA, luna)).contains(review);
        assertThat(subject.findByAuthorAndProduct(ANA, sol)).isEmpty();
    }

    @Test
    void givenAModeratedReview_whenItsAuthorResubmitsIt_thenItIsReplacedAndBackToPending() {
        Review first = review(ANA, luna, 5, Optional.of("Muy cómodo"), T0);
        subject.save(first);
        moderated(first.id(), ReviewStatus.APPROVED, T0.plusSeconds(60));
        Review stored = subject.findByAuthorAndProduct(ANA, luna).orElseThrow();
        assertThat(stored.status()).isEqualTo(ReviewStatus.APPROVED);

        Review edited = stored.resubmit(new ReviewContent(2, Optional.empty()), new AuthorName("Anita"),
            T0.plusSeconds(3_600));
        assertThat(subject.save(edited)).isEqualTo(SaveReview.Outcome.REPLACED);
        persistAndForget();

        assertThat(subject.findByAuthorAndProduct(ANA, luna)).contains(edited);
    }

    @Test
    void givenAStoredReview_whenAnotherOneOfTheSameAuthorAndProductIsSaved_thenItReplacesItAndKeepsItsIdAndCreation() {
        Review first = review(ANA, luna, 5, Optional.empty(), T0);
        subject.save(first);
        // As when two first submissions race: the second one was built without seeing the first.
        Review racing = review(ANA, luna, 3, Optional.of("Raced"), T0.plusSeconds(1));

        assertThat(subject.save(racing)).isEqualTo(SaveReview.Outcome.REPLACED);
        persistAndForget();

        Review stored = subject.findByAuthorAndProduct(ANA, luna).orElseThrow();
        assertThat(stored.id()).isEqualTo(first.id());
        assertThat(stored.createdAt()).isEqualTo(T0);
        assertThat(stored.updatedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(stored.content()).isEqualTo(new ReviewContent(3, Optional.of("Raced")));
        assertThat(subject.byAuthor(ANA)).hasSize(1);
    }

    @Test
    void givenReviewsOfTwoCustomers_whenReadingOneAuthorsReviews_thenOnlyItsOwnComeOldestFirst() {
        Review newer = review(ANA, sol, 4, Optional.empty(), T0.plusSeconds(60));
        Review older = review(ANA, luna, 5, Optional.empty(), T0);
        subject.save(newer);
        subject.save(older);
        subject.save(review(BRUNO, luna, 1, Optional.empty(), T0));
        persistAndForget();

        assertThat(subject.byAuthor(ANA)).containsExactly(older, newer);
    }

    @Test
    void givenReviewsOfTwoCustomers_whenOnesAreRemoved_thenOnlyItsOwnGoAndASecondRemovalFindsNone() {
        subject.save(review(ANA, luna, 5, Optional.empty(), T0));
        subject.save(review(ANA, sol, 4, Optional.empty(), T0));
        Review brunos = review(BRUNO, luna, 1, Optional.empty(), T0);
        subject.save(brunos);

        assertThat(subject.removeByAuthor(ANA)).isEqualTo(2);
        assertThat(subject.removeByAuthor(ANA)).isZero();
        persistAndForget();

        assertThat(subject.byAuthor(ANA)).isEmpty();
        assertThat(subject.findByAuthorAndProduct(BRUNO, luna)).contains(brunos);
    }
}
