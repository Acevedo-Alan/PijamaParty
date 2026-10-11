package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.FilterOptions;
import com.pijamaparty.store.catalog.application.FilterOptionsQuery;
import com.pijamaparty.store.catalog.application.FilterOptionsQueryContractTest;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows (as {@code JdbcProductStoreContractTest}), with the stock in the inventory's table. */
@ApiTest
class JdbcFilterOptionsQueryContractTest
    extends FilterOptionsQueryContractTest<JdbcFilterOptionsQueryContractTest.InTransaction> {

    private final ListingTables tables;
    private final InTransaction inTransaction;

    JdbcFilterOptionsQueryContractTest(JdbcProductListingQuery adapter, JdbcCategoryStore categories,
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
    record InTransaction(JdbcProductListingQuery adapter, TransactionTemplate transactions)
        implements FilterOptionsQuery {

        @Override
        public FilterOptions options(Optional<CategoryId> category) {
            return transactions.execute(status -> adapter.options(category));
        }
    }
}
