package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the data download's read-only transaction, as the customer itself. */
@Service
class ExportCustomerReviewsService implements ExportCustomerReviews {

    private final CustomerReviews reviews;
    private final ProductNames productNames;

    ExportCustomerReviewsService(CustomerReviews reviews, ProductNames productNames) {
        this.reviews = reviews;
        this.productNames = productNames;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ExportedReview> execute(Query query) {
        List<Review> own = reviews.byAuthor(query.customer());
        if (own.isEmpty()) {
            return List.of();
        }
        Map<ProductId, String> names = productNames.namesOf(own.stream().map(Review::product).toList());
        return own.stream().map(review -> new ExportedReview(Optional.ofNullable(names.get(review.product())),
            review.content().rating(), review.content().text(), review.status(), review.createdAt(),
            review.updatedAt())).toList();
    }
}
