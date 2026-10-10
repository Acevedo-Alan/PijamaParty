package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.CategoryStoreContractTest;
import com.pijamaparty.store.catalog.application.LoadCategories;
import com.pijamaparty.store.catalog.application.RemoveCategory;
import com.pijamaparty.store.catalog.application.SaveCategory;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
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
 * The adapter runs in its caller's transaction. Here every call of the contract is its own committed transaction, and
 * the last test checks that a violated constraint leaves the caller's transaction usable and committable, which needs
 * a real commit. Not {@code @PersistenceAdapterTest}: its rolled-back test transaction was not active in the contract
 * methods inherited from {@code CategoryStoreContractTest} (the adapter refuses to write without one).
 */
@ApiTest
class JdbcCategoryStoreContractTest extends CategoryStoreContractTest<JdbcCategoryStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcCategoryStore adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    JdbcCategoryStoreContractTest(JdbcCategoryStore adapter, PlatformTransactionManager transactionManager,
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
    protected void givenAProductIn(CategoryId category) {
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Short de algodón', 'short-de-algodon', '', 'Algodón', 'SUMMER', 'WOMEN', false, now(), now())
            """, UUID.randomUUID(), category.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, category");
    }

    @Test
    void givenViolatedConstraints_whenTheSameTransactionGoesOn_thenItsOtherWritesAreCommitted() {
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        Category withProducts = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Verano"),
            List.of(), now);
        inTransaction.add(withProducts);
        givenAProductIn(withProducts.id());
        Category clash = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("VERANO"),
            List.of(withProducts), now);
        Category other = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Invierno"),
            List.of(withProducts), now);

        transactions.executeWithoutResult(status -> {
            assertThat(adapter.add(clash)).isEqualTo(SaveCategory.Outcome.SLUG_ALREADY_USED);
            assertThat(adapter.remove(withProducts.id())).isEqualTo(RemoveCategory.Outcome.HAS_PRODUCTS);
            assertThat(adapter.add(other)).isEqualTo(SaveCategory.Outcome.SAVED);
        });

        assertThat(inTransaction.findAll()).extracting(Category::id).containsExactly(withProducts.id(), other.id());
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcCategoryStore adapter, TransactionTemplate transactions)
        implements LoadCategories, SaveCategory, RemoveCategory {

        @Override
        public Optional<Category> findById(CategoryId id) {
            return transactions.execute(status -> adapter.findById(id));
        }

        @Override
        public List<Category> findAll() {
            return transactions.execute(status -> adapter.findAll());
        }

        @Override
        public SaveCategory.Outcome add(Category category) {
            return transactions.execute(status -> adapter.add(category));
        }

        @Override
        public SaveCategory.Outcome update(Category category) {
            return transactions.execute(status -> adapter.update(category));
        }

        @Override
        public RemoveCategory.Outcome remove(CategoryId id) {
            return transactions.execute(status -> adapter.remove(id));
        }
    }
}
