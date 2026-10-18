package com.pijamaparty.store.reviews.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * UC-24: the approved ratings of a product, as the product page shows them. The average is computed when read, not
 * stored (T-E5-01).
 *
 * @param count the number of approved reviews
 * @param ratingSum the sum of their ratings
 */
public record RatingSummary(long count, long ratingSum) {

    /** The scale of the average: "4,5" (UC-24). */
    public static final int AVERAGE_DECIMALS = 1;

    public static final RatingSummary NONE = new RatingSummary(0, 0);

    public RatingSummary {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (ratingSum < count * ReviewContent.MIN_RATING || ratingSum > count * ReviewContent.MAX_RATING) {
            throw new IllegalArgumentException("the ratings must add up to what " + count + " ratings can");
        }
    }

    /** The mean of the approved ratings with one decimal, half up; empty without approved reviews. */
    public Optional<BigDecimal> average() {
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(ratingSum)
            .divide(BigDecimal.valueOf(count), AVERAGE_DECIMALS, RoundingMode.HALF_UP));
    }
}
