package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.SaveCategory;
import com.pijamaparty.store.catalog.application.SaveProduct;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.VariantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Arranges the tables that {@link JdbcProductListingQuery} reads, each write committed through the real adapters (the
 * stock as the inventory stores it), and empties them after each test.
 */
final class ListingTables {

    private final JdbcCategoryStore categories;
    private final JdbcProductStore products;
    private final TransactionTemplate transactions;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    ListingTables(JdbcCategoryStore categories, JdbcProductStore products,
                  PlatformTransactionManager transactionManager, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.categories = categories;
        this.products = products;
        this.transactions = new TransactionTemplate(transactionManager);
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    TransactionTemplate transactions() {
        return transactions;
    }

    void add(Category category) {
        if (transactions.execute(status -> categories.add(category)) != SaveCategory.Outcome.SAVED) {
            throw new IllegalArgumentException("category not saved");
        }
    }

    void update(Category category) {
        if (transactions.execute(status -> categories.update(category)) != SaveCategory.Outcome.SAVED) {
            throw new IllegalArgumentException("category not saved");
        }
    }

    void add(Product product) {
        if (transactions.execute(status -> products.add(product)) != SaveProduct.Outcome.SAVED) {
            throw new IllegalArgumentException("product not saved");
        }
    }

    void setAvailable(VariantId variant, int available) {
        app.update("""
            insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())
            on conflict (variant_id) do update set on_hand = excluded.on_hand
            """, variant.value(), available);
    }

    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
    }
}
