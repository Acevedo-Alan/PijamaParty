package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.ListedProduct;
import com.pijamaparty.store.catalog.application.ProductSearchContractTest;
import com.pijamaparty.store.catalog.application.ProductSearchQuery;
import com.pijamaparty.store.catalog.application.ProductSuggestion;
import com.pijamaparty.store.catalog.application.SearchVectors;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.SearchText;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows (as {@code JdbcProductListingQueryContractTest}), on the real spanish configuration. */
@ApiTest
class JdbcProductSearchContractTest extends ProductSearchContractTest<JdbcProductSearchContractTest.InTransaction> {

    private final ListingTables tables;
    private final InTransaction inTransaction;

    JdbcProductSearchContractTest(JdbcProductSearch adapter, JdbcCategoryStore categories, JdbcProductStore products,
                                  PlatformTransactionManager transactionManager, JdbcTemplate app,
                                  PostgreSQLContainer<?> postgres) {
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
    protected void givenCategoryUpdated(Category category) {
        tables.update(category);
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
    record InTransaction(JdbcProductSearch adapter, TransactionTemplate transactions)
        implements ProductSearchQuery, SearchVectors {

        @Override
        public List<ListedProduct> search(SearchText text, ProductFilter filter, Optional<ProductSort> sort,
                                          int offset, int limit) {
            return transactions.execute(status -> adapter.search(text, filter, sort, offset, limit));
        }

        @Override
        public long count(SearchText text, ProductFilter filter) {
            return transactions.execute(status -> adapter.count(text, filter));
        }

        @Override
        public List<ProductSuggestion> suggest(SearchText text, int limit) {
            return transactions.execute(status -> adapter.suggest(text, limit));
        }

        @Override
        public void refreshProduct(ProductId product) {
            transactions.executeWithoutResult(status -> adapter.refreshProduct(product));
        }

        @Override
        public void refreshCategory(CategoryId category) {
            transactions.executeWithoutResult(status -> adapter.refreshCategory(category));
        }
    }
}
