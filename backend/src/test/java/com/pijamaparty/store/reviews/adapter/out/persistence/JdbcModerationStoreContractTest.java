package com.pijamaparty.store.reviews.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import com.pijamaparty.store.reviews.application.LockReview;
import com.pijamaparty.store.reviews.application.ModerationStoreContractTest;
import com.pijamaparty.store.reviews.application.RemoveReview;
import com.pijamaparty.store.reviews.application.ReviewsForModeration;
import com.pijamaparty.store.reviews.application.SaveModeration;
import com.pijamaparty.store.reviews.domain.Review;
import com.pijamaparty.store.reviews.domain.ReviewId;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the administrator, so Row Level Security and the update guard of V37
 * apply as in production (RNF-27), then read back from PostgreSQL. The reviews are stored as the owner.
 */
@ApiTest
class JdbcModerationStoreContractTest extends ModerationStoreContractTest<JdbcModerationStoreContractTest.AsAdmin> {

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    JdbcModerationStoreContractTest(JdbcReviewStore adapter, PlatformTransactionManager transactionManager,
                                    PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdmin createSubject() {
        return asAdmin;
    }

    @Override
    protected void stored(Review review) {
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                                moderated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, review.id().value(), review.product().value(), review.author().value(), review.content().rating(),
            review.content().text().orElse(null), review.authorName().value(), review.status().name(),
            Timestamp.from(review.createdAt()), Timestamp.from(review.updatedAt()),
            review.moderatedAt().map(Timestamp::from).orElse(null));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
    }

    record AsAdmin(JdbcReviewStore adapter, TransactionTemplate transactions)
        implements ReviewsForModeration, LockReview, SaveModeration, RemoveReview {

        @Override
        public List<Review> withStatus(ReviewStatus status, int offset, int limit) {
            return asAdmin(() -> adapter.withStatus(status, offset, limit));
        }

        @Override
        public Optional<Review> lockById(ReviewId id) {
            return asAdmin(() -> adapter.lockById(id));
        }

        @Override
        public void saveDecision(Review review) {
            asAdmin(() -> {
                adapter.saveDecision(review);
                return null;
            });
        }

        @Override
        public boolean remove(ReviewId id) {
            return asAdmin(() -> adapter.remove(id));
        }

        private <R> R asAdmin(Supplier<R> work) {
            return CurrentActor.callAs(RlsPolicyTester.ADMIN, () -> transactions.execute(status -> work.get()));
        }
    }
}
