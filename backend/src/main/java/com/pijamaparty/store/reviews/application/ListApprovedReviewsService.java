package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the actor, whoever it is: the database shows everyone the approved reviews (V37). */
@Service
class ListApprovedReviewsService implements ListApprovedReviews {

    private final ListedProducts listedProducts;
    private final ApprovedReviews approvedReviews;

    ListApprovedReviewsService(ListedProducts listedProducts, ApprovedReviews approvedReviews) {
        this.listedProducts = listedProducts;
        this.approvedReviews = approvedReviews;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<ProductId> product = listedProducts.bySlug(query.slug());
        if (product.isEmpty()) {
            return new Result.ProductNotFound();
        }
        return new Result.Listed(approvedReviews.summaryOf(product.get()),
            approvedReviews.newestOf(product.get(), query.page() * PAGE_SIZE, PAGE_SIZE));
    }
}
