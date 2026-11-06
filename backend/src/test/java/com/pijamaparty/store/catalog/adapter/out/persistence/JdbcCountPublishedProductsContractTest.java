package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.CountPublishedProducts;
import com.pijamaparty.store.catalog.application.CountPublishedProductsContractTest;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows (as {@code JdbcStockWatchContractTest}). */
@ApiTest
class JdbcCountPublishedProductsContractTest
    extends CountPublishedProductsContractTest<JdbcCountPublishedProductsContractTest.InTransaction> {

    private final ListingTables tables;
    private final InTransaction inTransaction;

    JdbcCountPublishedProductsContractTest(JdbcStockWatch adapter, JdbcCategoryStore categories,
                                           JdbcProductStore products, PlatformTransactionManager transactionManager,
                                           JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.tables = new ListingTables(categories, products, transactionManager, app, postgres);
        this.inTransaction = new InTransaction(adapter, tables.transactions());
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @Override
    protected void givenCategory(Category category) {
        tables.add(category);
    }

    @Override
    protected void givenProduct(Product product) {
        tables.add(product);
    }

    @Override
    protected void givenAvailable(VariantId variant, int available) {
        tables.setAvailable(variant, available);
    }

    @AfterEach
    void cleanUp() {
        tables.cleanUp();
    }

    /** Each call in its own transaction, as a use case would run it. */
    record InTransaction(JdbcStockWatch adapter, TransactionTemplate transactions) implements CountPublishedProducts {

        @Override
        public long publishedProducts() {
            Long count = transactions.execute(status -> adapter.publishedProducts());
            return count == null ? 0 : count;
        }
    }
}
