package com.pijamaparty.store.reviews.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-24, UC-25: one customer's review of one product. Every submission waits for moderation
 * ({@link ReviewStatus#PENDING}); the administrator's decision sets {@code moderatedAt}. Personal data of its author
 * (R-05): {@link #toString()} prints neither its text nor its author's name.
 *
 * @param updatedAt the last submission
 * @param moderatedAt empty while pending
 */
public record Review(ReviewId id, ProductId product, UserId author, ReviewContent content, AuthorName authorName,
                     ReviewStatus status, Instant createdAt, Instant updatedAt, Optional<Instant> moderatedAt) {

    public Review {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(moderatedAt, "moderatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("a review cannot be submitted before it was created");
        }
        if ((status == ReviewStatus.PENDING) != moderatedAt.isEmpty()) {
            throw new IllegalArgumentException("only a moderated review has a moderation time");
        }
    }

    /** UC-24: the customer's first review of the product, which waits for moderation. */
    public static Review submit(ReviewId id, ProductId product, UserId author, ReviewContent content,
                                AuthorName authorName, Instant now) {
        return new Review(id, product, author, content, authorName, ReviewStatus.PENDING, now, now,
            Optional.empty());
    }

    /**
     * UC-24: the author edits its review. The rating, text and name are replaced and the review goes back to
     * moderation, so it leaves the product page and the average until it is approved again.
     */
    public Review resubmit(ReviewContent newContent, AuthorName newAuthorName, Instant now) {
        Instant submittedAt = now.isBefore(createdAt) ? createdAt : now;
        return new Review(id, product, author, newContent, newAuthorName, ReviewStatus.PENDING, createdAt,
            submittedAt, Optional.empty());
    }

    /**
     * UC-25: the administrator approves the review, in any status, so it shows on the product page and counts toward
     * the average. An approved review stays as it is, with its moderation time.
     */
    public Review approved(Instant now) {
        return decided(ReviewStatus.APPROVED, now);
    }

    /**
     * UC-25: the administrator rejects the review, in any status, so it leaves the product page and the average. A
     * rejected review stays as it is, with its moderation time.
     */
    public Review rejected(Instant now) {
        return decided(ReviewStatus.REJECTED, now);
    }

    private Review decided(ReviewStatus decision, Instant now) {
        if (status == decision) {
            return this;
        }
        return new Review(id, product, author, content, authorName, decision, createdAt, updatedAt,
            Optional.of(now));
    }

    @Override
    public String toString() {
        return "Review[id=" + id + ", product=" + product + ", rating=" + content.rating() + ", status=" + status
            + ", ***]";
    }
}
