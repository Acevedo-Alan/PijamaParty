package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.LoadProducts;
import com.pijamaparty.store.catalog.application.ProductStoreContractTest;
import com.pijamaparty.store.catalog.application.ProductSummaries;
import com.pijamaparty.store.catalog.application.ProductSummaryView;
import com.pijamaparty.store.catalog.application.SaveProduct;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. As for {@code JdbcCategoryStoreContractTest}, every call of the
 * contract is its own committed transaction, and the last tests check that a violated constraint leaves the caller's
 * transaction usable and that writes need one.
 */
@ApiTest
class JdbcProductStoreContractTest extends ProductStoreContractTest<JdbcProductStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcProductStore adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    JdbcProductStoreContractTest(JdbcProductStore adapter, PlatformTransactionManager transactionManager,
                                 JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.inTransaction = new InTransaction(adapter, transactions);
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @Override
    protected CategoryId givenACategory() {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, id, "verano-" + id.toString().substring(0, 8));
        return CategoryId.of(id);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, size_guide, category");
    }

    @Test
    void givenViolatedConstraints_whenTheSameTransactionGoesOn_thenItsOtherWritesAreCommitted() {
        CategoryId category = givenACategory();
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        Product existing = product(category, "Pijama", now);
        inTransaction.add(existing);
        Product clash = product(category, "PIJAMA", now);
        Product unknownCategory = product(CategoryId.of(UUID.randomUUID()), "Bata", now);
        Product other = product(category, "Camisón", now);

        transactions.executeWithoutResult(status -> {
            assertThat(adapter.add(clash)).isEqualTo(SaveProduct.Outcome.SLUG_ALREADY_USED);
            assertThat(adapter.add(unknownCategory)).isEqualTo(SaveProduct.Outcome.UNKNOWN_CATEGORY);
            assertThat(adapter.add(other)).isEqualTo(SaveProduct.Outcome.SAVED);
        });

        assertThat(inTransaction.list(0, 10)).extracting(ProductSummaryView::id)
            .containsExactlyInAnyOrder(existing.id(), other.id());
    }

    @Test
    void givenNoTransaction_whenAProductIsWritten_thenItIsRefused() {
        Product product = product(givenACategory(), "Pijama", Instant.parse("2026-10-10T12:00:00Z"));

        assertThatThrownBy(() -> adapter.add(product)).hasMessageContaining("within the caller");
    }

    private static Product product(CategoryId category, String name, Instant now) {
        return Product.create(ProductId.of(UUID.randomUUID()), new ProductDetails(category, new ProductName(name),
            new Description(""), new Material("Algodón"), Season.SUMMER, Audience.WOMEN), now);
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcProductStore adapter, TransactionTemplate transactions)
        implements LoadProducts, SaveProduct, ProductSummaries {

        @Override
        public Optional<Product> findById(ProductId id) {
            return transactions.execute(status -> adapter.findById(id));
        }

        @Override
        public Optional<Product> findBySlug(Slug slug) {
            return transactions.execute(status -> adapter.findBySlug(slug));
        }

        @Override
        public Optional<Product> lockById(ProductId id) {
            return transactions.execute(status -> adapter.lockById(id));
        }

        @Override
        public Optional<Product> lockByVariantId(VariantId id) {
            return transactions.execute(status -> adapter.lockByVariantId(id));
        }

        @Override
        public List<ProductSummaryView> list(int offset, int limit) {
            return transactions.execute(status -> adapter.list(offset, limit));
        }

        @Override
        public SaveProduct.Outcome add(Product product) {
            return transactions.execute(status -> adapter.add(product));
        }

        @Override
        public SaveProduct.Outcome update(Product product) {
            return transactions.execute(status -> adapter.update(product));
        }
    }
}
