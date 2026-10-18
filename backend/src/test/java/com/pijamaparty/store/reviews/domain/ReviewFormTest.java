package com.pijamaparty.store.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC-24: the rules of a review as typed. */
class ReviewFormTest {

    @Test
    void givenEveryRatingFrom1To5_whenValidated_thenEachIsAccepted() {
        for (int rating = 1; rating <= 5; rating++) {
            assertThat(new ReviewForm(BigDecimal.valueOf(rating), null).validate())
                .isEqualTo(new ReviewForm.Outcome.Valid(new ReviewContent(rating, Optional.empty())));
        }
    }

    @Test
    void givenARatingOutside1To5AFractionOrNone_whenValidated_thenTheRatingIsNamed() {
        for (BigDecimal rating : new BigDecimal[] {BigDecimal.ZERO, BigDecimal.valueOf(6), new BigDecimal("4.5"),
            new BigDecimal("1e20"), null}) {
            assertThat(new ReviewForm(rating, "Muy cómodo").validate()).as("rating " + rating)
                .isEqualTo(new ReviewForm.Outcome.Invalid(Set.of(ReviewForm.Field.RATING)));
        }
    }

    @Test
    void givenAWholeRatingWrittenWithDecimals_whenValidated_thenItIsAccepted() {
        assertThat(new ReviewForm(new BigDecimal("4.0"), null).validate())
            .isEqualTo(new ReviewForm.Outcome.Valid(new ReviewContent(4, Optional.empty())));
    }

    @Test
    void givenATextWithSurroundingSpaces_whenValidated_thenItIsTrimmed() {
        assertThat(new ReviewForm(BigDecimal.valueOf(5), "  Muy cómodo \n").validate())
            .isEqualTo(new ReviewForm.Outcome.Valid(new ReviewContent(5, Optional.of("Muy cómodo"))));
    }

    @Test
    void givenABlankText_whenValidated_thenTheReviewHasNoText() {
        for (String text : new String[] {null, "", "   "}) {
            assertThat(new ReviewForm(BigDecimal.valueOf(3), text).validate()).as("[" + text + "]")
                .isEqualTo(new ReviewForm.Outcome.Valid(new ReviewContent(3, Optional.empty())));
        }
    }

    @Test
    void givenATextOf1000Characters_whenValidated_thenItIsAcceptedAndOneMoreIsNot() {
        assertThat(new ReviewForm(BigDecimal.ONE, "ñ".repeat(1_000)).validate())
            .isInstanceOf(ReviewForm.Outcome.Valid.class);
        assertThat(new ReviewForm(BigDecimal.ONE, "ñ".repeat(1_001)).validate())
            .isEqualTo(new ReviewForm.Outcome.Invalid(Set.of(ReviewForm.Field.TEXT)));
    }

    @Test
    void givenABadRatingAndATooLongText_whenValidated_thenBothAreNamed() {
        assertThat(new ReviewForm(BigDecimal.valueOf(7), "x".repeat(1_001)).validate())
            .isEqualTo(new ReviewForm.Outcome.Invalid(Set.of(ReviewForm.Field.RATING, ReviewForm.Field.TEXT)));
    }

    @Test
    void givenAForm_whenPrinted_thenItsTextIsNot() {
        assertThat(new ReviewForm(BigDecimal.ONE, "Muy cómodo").toString()).doesNotContain("cómodo");
    }
}
