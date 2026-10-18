package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import java.util.List;

/**
 * Outbound read port (UC-24): the approved reviews of a product, as the product page reads them, in the caller's
 * transaction.
 */
public interface ApprovedReviews {

    /** The number and the sum of the approved ratings of the product; {@link RatingSummary#NONE} without any. */
    RatingSummary summaryOf(ProductId product);

    /**
     * Up to {@code limit} approved reviews of the product, newest first (creation time, then id), skipping the first
     * {@code offset}.
     */
    List<PublishedReview> newestOf(ProductId product, int offset, int limit);
}
