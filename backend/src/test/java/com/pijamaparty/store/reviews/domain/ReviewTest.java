package com.pijamaparty.store.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-24, UC-25: a review, its moderation and its author's name. */
class ReviewTest {

    private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");
    private static final ProductId PRODUCT = ProductId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    private static Review submitted() {
        return Review.submit(ReviewId.of(UUID.randomUUID()), PRODUCT, ANA, new ReviewContent(4, Optional.empty()),
            new AuthorName("Ana"), T0);
    }

    @Test
    void givenAFirstReview_whenSubmitted_thenItWaitsForModeration() {
        Review review = submitted();

        assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(review.moderatedAt()).isEmpty();
        assertThat(review.createdAt()).isEqualTo(T0);
        assertThat(review.updatedAt()).isEqualTo(T0);
    }

    @Test
    void givenAnApprovedReview_whenItsAuthorEditsIt_thenItGoesBackToModerationAndKeepsItsIdAndCreation() {
        Review approved = new Review(ReviewId.of(UUID.randomUUID()), PRODUCT, ANA,
            new ReviewContent(4, Optional.empty()), new AuthorName("Ana"), ReviewStatus.APPROVED, T0, T0,
            Optional.of(T0.plusSeconds(60)));

        Review edited = approved.resubmit(new ReviewContent(2, Optional.of("Destiñe")), new AuthorName("Anita"),
            T0.plusSeconds(3_600));

        assertThat(edited.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(edited.moderatedAt()).isEmpty();
        assertThat(edited.content()).isEqualTo(new ReviewContent(2, Optional.of("Destiñe")));
        assertThat(edited.authorName()).isEqualTo(new AuthorName("Anita"));
        assertThat(edited.id()).isEqualTo(approved.id());
        assertThat(edited.createdAt()).isEqualTo(T0);
        assertThat(edited.updatedAt()).isEqualTo(T0.plusSeconds(3_600));
    }

    @Test
    void givenAClockBehindTheCreation_whenEdited_thenTheSubmissionIsNotBeforeTheCreation() {
        assertThat(submitted().resubmit(new ReviewContent(5, Optional.empty()), new AuthorName("Ana"),
            T0.minusSeconds(5)).updatedAt()).isEqualTo(T0);
    }

    @Test
    void givenAStatusAndAModerationTimeThatDisagree_whenCreated_thenItIsRefused() {
        var content = new ReviewContent(4, Optional.empty());
        assertThatThrownBy(() -> new Review(ReviewId.of(UUID.randomUUID()), PRODUCT, ANA, content,
            new AuthorName("Ana"), ReviewStatus.APPROVED, T0, T0, Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Review(ReviewId.of(UUID.randomUUID()), PRODUCT, ANA, content,
            new AuthorName("Ana"), ReviewStatus.PENDING, T0, T0, Optional.of(T0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAPendingReview_whenApprovedOrRejected_thenOnlyItsStatusAndModerationTimeChange() {
        Review pending = submitted();

        Review approved = pending.approved(T0.plusSeconds(60));
        Review rejected = pending.rejected(T0.plusSeconds(120));

        assertThat(approved.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(approved.moderatedAt()).contains(T0.plusSeconds(60));
        assertThat(rejected.status()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(rejected.moderatedAt()).contains(T0.plusSeconds(120));
        for (Review decided : new Review[] {approved, rejected}) {
            assertThat(decided.id()).isEqualTo(pending.id());
            assertThat(decided.content()).isEqualTo(pending.content());
            assertThat(decided.authorName()).isEqualTo(pending.authorName());
            assertThat(decided.createdAt()).isEqualTo(pending.createdAt());
            assertThat(decided.updatedAt()).isEqualTo(pending.updatedAt());
        }
    }

    @Test
    void givenADecidedReview_whenTheOtherDecisionIsTaken_thenItChangesWithANewModerationTime() {
        Review approved = submitted().approved(T0.plusSeconds(60));

        Review rejected = approved.rejected(T0.plusSeconds(120));

        assertThat(rejected.status()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(rejected.moderatedAt()).contains(T0.plusSeconds(120));
        assertThat(rejected.approved(T0.plusSeconds(180)).moderatedAt()).contains(T0.plusSeconds(180));
    }

    @Test
    void givenADecidedReview_whenTheSameDecisionIsRepeated_thenNothingChanges() {
        Review approved = submitted().approved(T0.plusSeconds(60));
        Review rejected = submitted().rejected(T0.plusSeconds(60));

        assertThat(approved.approved(T0.plusSeconds(120))).isEqualTo(approved);
        assertThat(rejected.rejected(T0.plusSeconds(120))).isEqualTo(rejected);
    }

    @Test
    void givenAnAccountName_whenTheAuthorNameIsTaken_thenItIsItsFirstWord() {
        assertThat(AuthorName.fromAccountName("  María José   Pérez ")).contains(new AuthorName("María"));
        assertThat(AuthorName.fromAccountName("Ana")).contains(new AuthorName("Ana"));
        assertThat(AuthorName.fromAccountName("x".repeat(150))).contains(new AuthorName("x".repeat(100)));
        assertThat(AuthorName.fromAccountName("   ")).isEmpty();
    }

    @Test
    void givenAReview_whenPrinted_thenNeitherItsTextNorItsAuthorsNameIs() {
        Review review = Review.submit(ReviewId.of(UUID.randomUUID()), PRODUCT, ANA,
            new ReviewContent(4, Optional.of("Muy cómodo")), new AuthorName("Ana"), T0);

        assertThat(review.toString()).doesNotContain("cómodo").doesNotContain("Ana");
    }
}
