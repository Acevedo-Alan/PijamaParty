package com.pijamaparty.store.reviews.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import com.pijamaparty.store.reviews.application.CustomerReviews;
import com.pijamaparty.store.reviews.application.LoadReview;
import com.pijamaparty.store.reviews.application.RemoveCustomerReviews;
import com.pijamaparty.store.reviews.application.ReviewStoreContractTest;
import com.pijamaparty.store.reviews.application.SaveReview;
import com.pijamaparty.store.reviews.domain.ProductId;
import com.pijamaparty.store.reviews.domain.Review;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the review's author, so Row Level Security applies as in production
 * (RNF-27), then read back from PostgreSQL. The authors have no account, as after its deletion, so they may delete
 * their reviews (V37).
 */
@ApiTest
class JdbcReviewStoreContractTest extends ReviewStoreContractTest<JdbcReviewStoreContractTest.AsAuthor> {

    private final AsAuthor asAuthor;
    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    JdbcReviewStoreContractTest(JdbcReviewStore adapter, PlatformTransactionManager transactionManager,
                                JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.asAuthor = new AsAuthor(adapter, new TransactionTemplate(transactionManager));
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAuthor createSubject() {
        return asAuthor;
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
    void givenAnotherCustomersReview_whenACustomerReadsOrRemovesIt_thenItSeesNoneAndItStays() {
        ProductId product = ProductId.of(UUID.randomUUID());
        Review anas = review(ANA, product, 5, Optional.empty(), T0);
        asAuthor.save(anas);

        assertThat(asAuthor.as(RlsPolicyTester.CUSTOMER_B, () -> asAuthor.adapter().findByAuthorAndProduct(ANA,
            product))).isEmpty();
        assertThat(asAuthor.as(RlsPolicyTester.CUSTOMER_B, () -> asAuthor.adapter().byAuthor(ANA))).isEmpty();
        assertThat(asAuthor.as(RlsPolicyTester.CUSTOMER_B, () -> asAuthor.adapter().removeByAuthor(ANA))).isZero();
        assertThat(asAuthor.findByAuthorAndProduct(ANA, product)).contains(anas);
    }

    @Test
    void givenAnotherCustomersReview_whenACustomerSavesOneInItsName_thenRowLevelSecurityRefusesIt() {
        ProductId product = ProductId.of(UUID.randomUUID());
        asAuthor.save(review(ANA, product, 5, Optional.empty(), T0));

        assertThatThrownBy(() -> asAuthor.as(RlsPolicyTester.CUSTOMER_B, () -> asAuthor.adapter().save(
                review(ANA, product, 1, Optional.of("Malo"), T0.plusSeconds(1)))))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThat(asAuthor.findByAuthorAndProduct(ANA, product).orElseThrow().content().rating()).isEqualTo(5);
    }

    record AsAuthor(JdbcReviewStore adapter, TransactionTemplate transactions)
        implements LoadReview, SaveReview, CustomerReviews, RemoveCustomerReviews {

        @Override
        public Optional<Review> findByAuthorAndProduct(UserId author, ProductId product) {
            return as(customer(author), () -> adapter.findByAuthorAndProduct(author, product));
        }

        @Override
        public Outcome save(Review review) {
            return as(customer(review.author()), () -> adapter.save(review));
        }

        @Override
        public List<Review> byAuthor(UserId author) {
            return as(customer(author), () -> adapter.byAuthor(author));
        }

        @Override
        public int removeByAuthor(UserId author) {
            return as(customer(author), () -> adapter.removeByAuthor(author));
        }

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }

        private static Actor customer(UserId author) {
            return Actor.customer(author, Optional.empty(), Optional.empty());
        }
    }
}
