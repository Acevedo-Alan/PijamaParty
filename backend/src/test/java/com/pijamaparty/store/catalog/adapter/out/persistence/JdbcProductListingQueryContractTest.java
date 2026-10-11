package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.ProductListingItem;
import com.pijamaparty.store.catalog.application.ProductListingQuery;
import com.pijamaparty.store.catalog.application.ProductListingQueryContractTest;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows (as {@code JdbcProductStoreContractTest}), with the stock in the inventory's table. */
@ApiTest
class JdbcProductListingQueryContractTest
    extends ProductListingQueryContractTest<JdbcProductListingQueryContractTest.InTransaction> {

    private final ListingTables tables;
    private final InTransaction inTransaction;

    JdbcProductListingQueryContractTest(JdbcProductListingQuery adapter, JdbcCategoryStore categories,
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
        implements ProductListingQuery {

        @Override
        public List<ProductListingItem> list(ProductFilter filter, ProductSort sort, int offset, int limit) {
            return transactions.execute(status -> adapter.list(filter, sort, offset, limit));
        }
    }
}
