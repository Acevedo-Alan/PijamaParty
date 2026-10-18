package com.pijamaparty.store.reviews.adapter.in.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerDataContributorContractTest;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.domain.ReviewStatus;
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
 * The reviews contributor of the data download, through the real use case and adapters: reviews stored by the owner
 * role, exported in a read-only transaction as the customer, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class ReviewsCustomerDataContractTest extends CustomerDataContributorContractTest<ReviewsCustomerData> {

    private static final String INSERT_REVIEW = """
        insert into review (id, product_id, user_id, rating, text, author_name, status, created_at, updated_at,
                            moderated_at)
        values (?, ?, ?, ?, ?, 'Ana', ?, now(), now(), case when ? = 'PENDING' then null else now() end)
        """;

    private final ReviewsCustomerData contributor;
    private final TransactionTemplate readOnly;
    private final JdbcTemplate owner;

    ReviewsCustomerDataContractTest(ReviewsCustomerData contributor, PlatformTransactionManager transactionManager,
                                    PostgreSQLContainer<?> postgres) {
        this.contributor = contributor;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected ReviewsCustomerData createSubject() {
        return contributor;
    }

    @Override
    protected CustomerDataSection expectedSection() {
        return CustomerDataSection.REVIEWS;
    }

    @Override
    protected UserId customerWith(int records) {
        UserId customer = UserId.of(UUID.randomUUID());
        for (int n = 0; n < records; n++) {
            review(customer, UUID.randomUUID(), "APPROVED", null);
        }
        return customer;
    }

    @Override
    protected List<?> exportAs(UserId customer, Supplier<List<?>> export) {
        return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
            () -> readOnly.execute(status -> export.get()));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate review");
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, category");
    }

    @Test
    void givenAPendingReviewOfACatalogProduct_whenExporting_thenItCarriesTheProductNameAndItsText() {
        UserId ana = customerWith(0);
        UUID product = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama Luna', ?, '', 'Algodón', 'SUMMER', 'WOMEN', false, now(), now())
            """, product, category, "pijama-luna-" + product);
        review(ana, product, "PENDING", "Muy cómodo");

        assertThat(exportAs(ana, () -> subject.export(ana))).singleElement()
            .isInstanceOfSatisfying(ReviewsCustomerData.ReviewRecord.class, review -> {
                assertThat(review.productName()).isEqualTo("Pijama Luna");
                assertThat(review.text()).isEqualTo("Muy cómodo");
                assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
                assertThat(review.rating()).isEqualTo(4);
            });
    }

    @Test
    void givenAnotherCustomersReviews_whenExportingThemAsThisCustomer_thenRowLevelSecurityHidesThem() {
        UserId ana = customerWith(0);
        UserId bruno = UserId.of(UUID.randomUUID());
        review(bruno, UUID.randomUUID(), "PENDING", null);

        assertThat(exportAs(ana, () -> subject.export(bruno))).isEmpty();
    }

    private void review(UserId customer, UUID product, String status, String text) {
        owner.update(INSERT_REVIEW, UUID.randomUUID(), product, customer.value(), 4, text, status, status);
    }
}
