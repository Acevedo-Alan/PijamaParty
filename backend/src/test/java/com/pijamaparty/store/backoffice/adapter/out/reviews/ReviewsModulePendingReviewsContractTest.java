package com.pijamaparty.store.backoffice.adapter.out.reviews;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.PendingReviewCount;
import com.pijamaparty.store.backoffice.application.PendingReviewCountContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the reviews module to {@code review}, read by the administrator in its own transaction,
 * so Row Level Security applies as in production (RNF-27). Reviews stored as the owner.
 */
@ApiTest
class ReviewsModulePendingReviewsContractTest extends PendingReviewCountContractTest<PendingReviewCount> {

    private final ReviewsModulePendingReviews adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    ReviewsModulePendingReviewsContractTest(ReviewsModulePendingReviews adapter,
                                            PlatformTransactionManager transactionManager,
                                            PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected PendingReviewCount createSubject() {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        return () -> {
            Long count = CurrentActor.callAs(admin, () -> transactions.execute(status -> adapter.pending()));
            return count == null ? 0 : count;
        };
    }

    @Override
    protected void givenReview(String status) {
        // Each review by its own author, of its own product (one review per author and product).
        owner.update("""
            insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                                moderated_at)
            values (?, ?, ?, 4, 'Lindo', 'Ana', ?, now(), now(), ?)
            """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status,
            status.equals("PENDING") ? null : Timestamp.from(Instant.now()));
    }

    // Reviews other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
    }
}
