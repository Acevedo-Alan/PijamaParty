package com.pijamaparty.store.reviews.adapter.in.accounts;

import com.pijamaparty.store.accounts.api.CustomerDataContributor;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.ExportCustomerReviews;
import com.pijamaparty.store.reviews.application.ExportCustomerReviews.ExportedReview;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * UC-28 (T-E5-06): the reviews section of the customer's data download, which {@code accounts} collects. Only
 * translates the use case's reviews into records ready to be written as JSON.
 */
@Component
class ReviewsCustomerData implements CustomerDataContributor {

    private final ExportCustomerReviews export;

    ReviewsCustomerData(ExportCustomerReviews export) {
        this.export = export;
    }

    @Override
    public CustomerDataSection section() {
        return CustomerDataSection.REVIEWS;
    }

    @Override
    public List<ReviewRecord> export(UserId customer) {
        return export.execute(new ExportCustomerReviews.Query(customer)).stream().map(ReviewRecord::of).toList();
    }

    /**
     * @param productName null when the catalog no longer has the product
     * @param updatedAt the last submission
     */
    record ReviewRecord(String productName, int rating, String text, ReviewStatus status, Instant createdAt,
                        Instant updatedAt) {

        static ReviewRecord of(ExportedReview review) {
            return new ReviewRecord(review.productName().orElse(null), review.rating(), review.text().orElse(null),
                review.status(), review.createdAt(), review.updatedAt());
        }

        @Override
        public String toString() {
            return "ReviewRecord[rating=" + rating + ", status=" + status + ", ***]";
        }
    }
}
