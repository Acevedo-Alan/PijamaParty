package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows it only its own orders (V14) and reviews (V37). */
@Service
class GetMyReviewService implements GetMyReview {

    private final ProductVariants productVariants;
    private final PurchasedProducts purchases;
    private final LoadReview loadReview;

    GetMyReviewService(ProductVariants productVariants, PurchasedProducts purchases, LoadReview loadReview) {
        this.productVariants = productVariants;
        this.purchases = purchases;
        this.loadReview = loadReview;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.CUSTOMER) || query.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        UserId customer = query.actor().userId().get();
        boolean eligible = productVariants.ofProduct(query.product())
            .map(variants -> purchases.boughtAnyOf(customer, variants))
            .orElse(false);
        return new Result.Found(eligible,
            loadReview.findByAuthorAndProduct(customer, query.product()).map(MyReview::of));
    }
}
