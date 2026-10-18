package com.pijamaparty.store.reviews.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.ApprovedReviews;
import com.pijamaparty.store.reviews.application.CustomerReviews;
import com.pijamaparty.store.reviews.application.LoadReview;
import com.pijamaparty.store.reviews.application.LockReview;
import com.pijamaparty.store.reviews.application.PublishedReview;
import com.pijamaparty.store.reviews.application.RemoveCustomerReviews;
import com.pijamaparty.store.reviews.application.RemoveReview;
import com.pijamaparty.store.reviews.application.ReviewsForModeration;
import com.pijamaparty.store.reviews.application.SaveModeration;
import com.pijamaparty.store.reviews.application.SaveReview;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reviews in table {@code review} (V37), in the caller's transaction. Row Level Security decides which reviews the
 * actor reaches and writes (RNF-27): a customer only its own, always pending moderation; the administrator every
 * review, only its status and moderation time.
 */
@Repository
class JdbcReviewStore implements LoadReview, SaveReview, CustomerReviews, RemoveCustomerReviews, ApprovedReviews,
    ReviewsForModeration, LockReview, SaveModeration, RemoveReview {

    private static final String SELECT = """
        SELECT id, product_id, user_id, rating, text, author_name, status, created_at, updated_at, moderated_at
        FROM review
        """;

    private static final String BY_AUTHOR_AND_PRODUCT = SELECT + """
        WHERE user_id = :author AND product_id = :product
        """;

    private static final String BY_AUTHOR = SELECT + """
        WHERE user_id = :author
        ORDER BY created_at, id
        """;

    // One review per customer and product (review_user_product_uk): a second submission, also one that raced with the
    // first, replaces the stored one and keeps its id and creation time. xmax is 0 only on a row this statement
    // inserted.
    private static final String UPSERT = """
        INSERT INTO review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                            moderated_at)
        VALUES (:id, :product, :author, :rating, :text, :authorName, :status, :createdAt, :updatedAt, :moderatedAt)
        ON CONFLICT (user_id, product_id) DO UPDATE
        SET rating = EXCLUDED.rating,
            text = EXCLUDED.text,
            author_name = EXCLUDED.author_name,
            status = EXCLUDED.status,
            updated_at = EXCLUDED.updated_at,
            moderated_at = EXCLUDED.moderated_at
        RETURNING (xmax = 0) AS created
        """;

    private static final String DELETE_BY_AUTHOR = """
        DELETE FROM review
        WHERE user_id = :author
        """;

    // The product page (UC-24) over the index review_product_status_idx. The status filter is explicit: a customer
    // also reaches its own pending and rejected reviews.
    private static final String APPROVED_SUMMARY = """
        SELECT count(*) AS count, coalesce(sum(rating), 0) AS rating_sum
        FROM review
        WHERE product_id = :product AND status = :approved
        """;

    private static final String APPROVED_NEWEST = """
        SELECT author_name, rating, text, created_at
        FROM review
        WHERE product_id = :product AND status = :approved
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """;

    // The moderation tabs (UC-25) over the index review_status_updated_at_idx: the pending queue from the oldest
    // submission, the decided reviews from the newest.
    private static final String PENDING_OLDEST_FIRST = SELECT + """
        WHERE status = :status
        ORDER BY updated_at, id
        LIMIT :limit OFFSET :offset
        """;

    private static final String DECIDED_NEWEST_FIRST = SELECT + """
        WHERE status = :status
        ORDER BY updated_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """;

    // The author's upsert waits for this lock, so a decision never overwrites an edit it did not see.
    private static final String LOCK_BY_ID = SELECT + """
        WHERE id = :id
        FOR UPDATE
        """;

    private static final String SAVE_DECISION = """
        UPDATE review
        SET status = :status,
            moderated_at = :moderatedAt
        WHERE id = :id
        """;

    private static final String DELETE_BY_ID = """
        DELETE FROM review
        WHERE id = :id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcReviewStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Review> findByAuthorAndProduct(UserId author, ProductId product) {
        return jdbc.query(BY_AUTHOR_AND_PRODUCT, new MapSqlParameterSource("author", author.value())
            .addValue("product", product.value()), JdbcReviewStore::row).stream().findFirst();
    }

    @Override
    public Outcome save(Review review) {
        Boolean created = jdbc.queryForObject(UPSERT, new MapSqlParameterSource()
            .addValue("id", review.id().value())
            .addValue("product", review.product().value())
            .addValue("author", review.author().value())
            .addValue("rating", review.content().rating())
            .addValue("text", review.content().text().orElse(null), Types.VARCHAR)
            .addValue("authorName", review.authorName().value())
            .addValue("status", review.status().name())
            .addValue("createdAt", review.createdAt().atOffset(ZoneOffset.UTC))
            .addValue("updatedAt", review.updatedAt().atOffset(ZoneOffset.UTC))
            .addValue("moderatedAt", review.moderatedAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null),
                Types.TIMESTAMP_WITH_TIMEZONE), Boolean.class);
        return Boolean.TRUE.equals(created) ? Outcome.CREATED : Outcome.REPLACED;
    }

    @Override
    public List<Review> byAuthor(UserId author) {
        return jdbc.query(BY_AUTHOR, new MapSqlParameterSource("author", author.value()), JdbcReviewStore::row);
    }

    @Override
    public int removeByAuthor(UserId author) {
        return jdbc.update(DELETE_BY_AUTHOR, new MapSqlParameterSource("author", author.value()));
    }

    @Override
    public RatingSummary summaryOf(ProductId product) {
        return jdbc.queryForObject(APPROVED_SUMMARY, approvedOf(product),
            (row, rowNumber) -> new RatingSummary(row.getLong("count"), row.getLong("rating_sum")));
    }

    @Override
    public List<PublishedReview> newestOf(ProductId product, int offset, int limit) {
        return jdbc.query(APPROVED_NEWEST, approvedOf(product).addValue("offset", offset).addValue("limit", limit),
            (row, rowNumber) -> new PublishedReview(
                new AuthorName(row.getString("author_name")),
                new ReviewContent(row.getInt("rating"), Optional.ofNullable(row.getString("text"))),
                row.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    @Override
    public List<Review> withStatus(ReviewStatus status, int offset, int limit) {
        String query = status == ReviewStatus.PENDING ? PENDING_OLDEST_FIRST : DECIDED_NEWEST_FIRST;
        return jdbc.query(query, new MapSqlParameterSource("status", status.name())
            .addValue("offset", offset)
            .addValue("limit", limit), JdbcReviewStore::row);
    }

    @Override
    public Optional<Review> lockById(ReviewId id) {
        return jdbc.query(LOCK_BY_ID, new MapSqlParameterSource("id", id.value()), JdbcReviewStore::row).stream()
            .findFirst();
    }

    @Override
    public void saveDecision(Review review) {
        int updated = jdbc.update(SAVE_DECISION, new MapSqlParameterSource()
            .addValue("id", review.id().value())
            .addValue("status", review.status().name())
            .addValue("moderatedAt", review.moderatedAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null),
                Types.TIMESTAMP_WITH_TIMEZONE));
        if (updated != 1) {
            throw new IllegalStateException("the review was locked for the decision, so it must still be stored");
        }
    }

    @Override
    public boolean remove(ReviewId id) {
        return jdbc.update(DELETE_BY_ID, new MapSqlParameterSource("id", id.value())) == 1;
    }

    private static MapSqlParameterSource approvedOf(ProductId product) {
        return new MapSqlParameterSource("product", product.value())
            .addValue("approved", ReviewStatus.APPROVED.name());
    }

    private static Review row(ResultSet row, int rowNumber) throws SQLException {
        return new Review(
            ReviewId.of(row.getObject("id", UUID.class)),
            ProductId.of(row.getObject("product_id", UUID.class)),
            UserId.of(row.getObject("user_id", UUID.class)),
            new ReviewContent(row.getInt("rating"), Optional.ofNullable(row.getString("text"))),
            new AuthorName(row.getString("author_name")),
            ReviewStatus.valueOf(row.getString("status")),
            row.getObject("created_at", OffsetDateTime.class).toInstant(),
            row.getObject("updated_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(row.getObject("moderated_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant));
    }
}
