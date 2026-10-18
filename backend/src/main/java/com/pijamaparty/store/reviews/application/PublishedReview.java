package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import java.time.Instant;
import java.util.Objects;

/**
 * What the product page shows of an approved review (UC-24): its author's first name, never who the author is.
 *
 * @param createdAt the customer's first submission
 */
public record PublishedReview(AuthorName authorName, ReviewContent content, Instant createdAt) {

    public PublishedReview {
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public String toString() {
        return "PublishedReview[rating=" + content.rating() + ", createdAt=" + createdAt + ", ***]";
    }
}
