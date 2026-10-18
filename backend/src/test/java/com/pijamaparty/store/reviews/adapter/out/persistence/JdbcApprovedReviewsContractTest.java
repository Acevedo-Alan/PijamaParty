package com.pijamaparty.store.reviews.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import com.pijamaparty.store.reviews.application.ApprovedReviews;
import com.pijamaparty.store.reviews.application.ApprovedReviewsContractTest;
import com.pijamaparty.store.reviews.application.PublishedReview;
import com.pijamaparty.store.reviews.application.SaveReview;
import com.pijamaparty.store.reviews.domain.AuthorName;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.RatingSummary;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewContent;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each review committed in its own transaction as its author and moderated as the administrator; the product page is
 * read as a guest, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class JdbcApprovedReviewsContractTest extends ApprovedReviewsContractTest<JdbcApprovedReviewsContractTest.AsVisitor> {

    private final AsVisitor asVisitor;
    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    JdbcApprovedReviewsContractTest(JdbcReviewStore adapter, PlatformTransactionManager transactionManager,
                                    JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.asVisitor = new AsVisitor(adapter, new TransactionTemplate(transactionManager));
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsVisitor createSubject() {
        return asVisitor;
    }

    @Override
    protected void moderated(ReviewId review, ReviewStatus status, Instant at) {
        rls.as(RlsPolicyTester.ADMIN, sql -> sql.update("update review set status = ?, moderated_at = ? where id = ?",
            status.name(), Timestamp.from(at), review.value()));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
    }

    @Test
    void givenACustomersPendingReview_whenItReadsTheProductPage_thenItsOwnReviewNeitherShowsNorCounts() {
        ProductId product = ProductId.of(UUID.randomUUID());
        asVisitor.save(Review.submit(ReviewId.of(UUID.randomUUID()), product, RlsPolicyTester.CUSTOMER_A_ID,
            new ReviewContent(5, Optional.empty()), new AuthorName("Ana"), T0));

        assertThat(asVisitor.as(RlsPolicyTester.CUSTOMER_A, () -> asVisitor.adapter().summaryOf(product)))
            .isEqualTo(RatingSummary.NONE);
        assertThat(asVisitor.as(RlsPolicyTester.CUSTOMER_A, () -> asVisitor.adapter().newestOf(product, 0, 10)))
            .isEmpty();
    }

    /** Saves as the review's author; reads the product page as a guest. */
    record AsVisitor(JdbcReviewStore adapter, TransactionTemplate transactions) implements SaveReview, ApprovedReviews {

        @Override
        public Outcome save(Review review) {
            return as(Actor.customer(review.author(), Optional.empty(), Optional.empty()), () -> adapter.save(review));
        }

        @Override
        public RatingSummary summaryOf(ProductId product) {
            return as(RlsPolicyTester.GUEST, () -> adapter.summaryOf(product));
        }

        @Override
        public List<PublishedReview> newestOf(ProductId product, int offset, int limit) {
            return as(RlsPolicyTester.GUEST, () -> adapter.newestOf(product, offset, limit));
        }

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
