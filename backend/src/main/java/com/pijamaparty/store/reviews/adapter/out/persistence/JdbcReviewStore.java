package com.pijamaparty.store.reviews.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.CustomerReviews;
import com.pijamaparty.store.reviews.application.LoadReview;
import com.pijamaparty.store.reviews.application.RemoveCustomerReviews;
import com.pijamaparty.store.reviews.application.SaveReview;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
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
 * actor reaches and writes (RNF-27): a customer only its own, always pending moderation.
 */
@Repository
class JdbcReviewStore implements LoadReview, SaveReview, CustomerReviews, RemoveCustomerReviews {

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
