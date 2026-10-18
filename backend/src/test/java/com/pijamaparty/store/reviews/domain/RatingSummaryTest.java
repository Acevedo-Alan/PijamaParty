package com.pijamaparty.store.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** UC-24: the average of the approved ratings, with one decimal. */
class RatingSummaryTest {

    @Test
    void givenApprovedRatingsOf4And5_whenAveraged_thenItIs4Point5() {
        assertThat(new RatingSummary(2, 9).average()).contains(new BigDecimal("4.5"));
    }

    @Test
    void givenAMeanWithMoreDecimals_whenAveraged_thenItIsRoundedHalfUpToOne() {
        assertThat(new RatingSummary(3, 13).average()).contains(new BigDecimal("4.3"));
        assertThat(new RatingSummary(4, 17).average()).contains(new BigDecimal("4.3"));
        assertThat(new RatingSummary(3, 14).average()).contains(new BigDecimal("4.7"));
        assertThat(new RatingSummary(1, 5).average()).contains(new BigDecimal("5.0"));
    }

    @Test
    void givenNoApprovedReviews_whenAveraged_thenThereIsNoAverage() {
        assertThat(RatingSummary.NONE.average()).isEmpty();
    }

    @Test
    void givenASumNoRatingsCanAddUpTo_whenBuilt_thenItIsRefused() {
        assertThatThrownBy(() -> new RatingSummary(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatingSummary(2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatingSummary(2, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RatingSummary(0, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
