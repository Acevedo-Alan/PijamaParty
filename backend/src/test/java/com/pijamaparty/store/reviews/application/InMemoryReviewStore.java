package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Fake of the review store: one review per author and product, as the unique constraint of V37. Locks nothing. */
public class InMemoryReviewStore
    implements LoadReview, SaveReview, CustomerReviews, RemoveCustomerReviews, ApprovedReviews, ReviewsForModeration,
    LockReview, SaveModeration, RemoveReview {

    private record Key(UserId author, ProductId product) {}

    private final Map<Key, Review> reviews = new LinkedHashMap<>();

    @Override
    public synchronized Optional<Review> findByAuthorAndProduct(UserId author, ProductId product) {
        return Optional.ofNullable(reviews.get(new Key(author, product)));
    }

    @Override
    public synchronized Outcome save(Review review) {
        Key key = new Key(review.author(), review.product());
        Review stored = reviews.get(key);
        if (stored == null) {
            reviews.put(key, review);
            return Outcome.CREATED;
        }
        reviews.put(key, new Review(stored.id(), review.product(), review.author(), review.content(),
            review.authorName(), review.status(), stored.createdAt(), review.updatedAt(), review.moderatedAt()));
        return Outcome.REPLACED;
    }

    @Override
    public synchronized List<Review> byAuthor(UserId author) {
        return reviews.values().stream()
            .filter(review -> review.author().equals(author))
            .sorted(Comparator.comparing(Review::createdAt).thenComparing(review -> review.id().value()))
            .toList();
    }

    @Override
    public synchronized int removeByAuthor(UserId author) {
        List<Key> own = reviews.keySet().stream().filter(key -> key.author().equals(author)).toList();
        own.forEach(reviews::remove);
        return own.size();
    }

    @Override
    public synchronized RatingSummary summaryOf(ProductId product) {
        List<Review> approved = approvedOf(product).toList();
        return new RatingSummary(approved.size(),
            approved.stream().mapToLong(review -> review.content().rating()).sum());
    }

    @Override
    public synchronized List<PublishedReview> newestOf(ProductId product, int offset, int limit) {
        return approvedOf(product)
            .sorted(Comparator.comparing(Review::createdAt).thenComparing(review -> review.id().value()).reversed())
            .skip(offset)
            .limit(limit)
            .map(review -> new PublishedReview(review.authorName(), review.content(), review.createdAt()))
            .toList();
    }

    @Override
    public synchronized List<Review> withStatus(ReviewStatus status, int offset, int limit) {
        Comparator<Review> oldestFirst = Comparator.comparing(Review::updatedAt)
            .thenComparing(review -> review.id().value());
        return reviews.values().stream()
            .filter(review -> review.status() == status)
            .sorted(status == ReviewStatus.PENDING ? oldestFirst : oldestFirst.reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized Optional<Review> lockById(ReviewId id) {
        return reviews.values().stream().filter(review -> review.id().equals(id)).findFirst();
    }

    @Override
    public synchronized void saveDecision(Review review) {
        Key key = new Key(review.author(), review.product());
        Review stored = reviews.get(key);
        if (stored == null || !stored.id().equals(review.id())) {
            throw new IllegalStateException("the review was locked for the decision, so it must still be stored");
        }
        reviews.put(key, new Review(stored.id(), stored.product(), stored.author(), stored.content(),
            stored.authorName(), review.status(), stored.createdAt(), stored.updatedAt(), review.moderatedAt()));
    }

    @Override
    public synchronized boolean remove(ReviewId id) {
        return reviews.values().removeIf(review -> review.id().equals(id));
    }

    private Stream<Review> approvedOf(ProductId product) {
        return reviews.values().stream()
            .filter(review -> review.product().equals(product) && review.status() == ReviewStatus.APPROVED);
    }

    /** UC-25: the administrator's decision on a stored review. */
    public synchronized void moderate(ReviewId id, ReviewStatus status, Instant at) {
        reviews.replaceAll((key, review) -> review.id().equals(id)
            ? new Review(review.id(), review.product(), review.author(), review.content(), review.authorName(),
                status, review.createdAt(), review.updatedAt(), Optional.of(at))
            : review);
    }

    public synchronized List<Review> all() {
        return List.copyOf(reviews.values());
    }
}
