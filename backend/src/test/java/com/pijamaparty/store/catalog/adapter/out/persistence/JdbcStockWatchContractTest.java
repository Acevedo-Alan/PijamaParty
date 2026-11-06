package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.catalog.application.ListingFixture;
import com.pijamaparty.store.catalog.application.StockWatch;
import com.pijamaparty.store.catalog.application.StockWatchContractTest;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows (as {@code JdbcProductListingQueryContractTest}), with the stock in the inventory's table. */
@ApiTest
class JdbcStockWatchContractTest extends StockWatchContractTest<JdbcStockWatchContractTest.InTransaction> {

    private final ListingTables tables;
    private final InTransaction inTransaction;

    JdbcStockWatchContractTest(JdbcStockWatch adapter, JdbcCategoryStore categories, JdbcProductStore products,
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
    protected void givenProduct(Product product) {
        tables.add(product);
    }

    @Override
    protected void givenAvailable(VariantId variant, int available) {
        tables.setAvailable(variant, available);
    }

    @Test
    void givenAVariantWith5OnHandAnd3Reserved_whenTheThresholdIs3_thenItShowsWith2Available() {
        CategoryId summer = category("Invierno", true).id();
        Product luna = stored(ListingFixture.product(summer, "Pijama Luna", 0, List.of("M"), List.of("Rosa"), 1000));
        VariantId medium = ListingFixture.variant(luna, "M", "Rosa");
        givenAvailable(medium, 5);
        tables.setReserved(medium, 3);

        assertThat(subject.lowStock(3, 10)).singleElement().extracting(LowStockVariant::available).isEqualTo(2L);
    }

    @AfterEach
    void cleanUp() {
        tables.cleanUp();
    }

    /** Each call in its own transaction, as a use case would run it. */
    record InTransaction(JdbcStockWatch adapter, TransactionTemplate transactions) implements StockWatch {

        @Override
        public List<LowStockVariant> lowStock(int threshold, int limit) {
            return transactions.execute(status -> adapter.lowStock(threshold, limit));
        }

        @Override
        public long lowStockCount(int threshold) {
            Long count = transactions.execute(status -> adapter.lowStockCount(threshold));
            return count == null ? 0 : count;
        }
    }
}
