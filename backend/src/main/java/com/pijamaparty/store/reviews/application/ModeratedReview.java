package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.application.ProductLinks.ProductLink;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the back office shows of a review (UC-25), with the name and slug of its product. Personal data of its author
 * (R-05): {@link #toString()} prints neither its text nor its author's name.
 *
 * @param productLink empty when the catalog no longer has the product
 * @param updatedAt the last submission
 */
public record ModeratedReview(ReviewId id, ProductId product, Optional<ProductLink> productLink,
                              AuthorName authorName, ReviewContent content, ReviewStatus status, Instant updatedAt) {

    public ModeratedReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(productLink, "productLink");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** The reviews in the same order, each with the link of its product, looked up once for all of them. */
    static List<ModeratedReview> of(List<Review> reviews, ProductLinks productLinks) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<ProductId, ProductLink> links = productLinks.linksOf(reviews.stream().map(Review::product).toList());
        return reviews.stream().map(review -> new ModeratedReview(review.id(), review.product(),
            Optional.ofNullable(links.get(review.product())), review.authorName(), review.content(), review.status(),
            review.updatedAt())).toList();
    }

    @Override
    public String toString() {
        return "ModeratedReview[id=" + id + ", product=" + product + ", rating=" + content.rating() + ", status="
            + status + ", ***]";
    }
}
