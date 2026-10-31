package com.pijamaparty.store.backoffice.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.CatalogFigures;
import com.pijamaparty.store.backoffice.application.CatalogFiguresContractTest;
import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the catalog module to its tables and the inventory's view, each read in its own
 * transaction. Rows stored as the owner, as the cart's catalog contract tests do.
 */
@ApiTest
class CatalogModuleFiguresContractTest extends CatalogFiguresContractTest<CatalogFigures> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;
    private final UUID category = UUID.randomUUID();

    CatalogModuleFiguresContractTest(CatalogModuleFigures adapter, PlatformTransactionManager transactionManager,
                                     PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogFigures createSubject() {
        return inTransaction;
    }

    @Override
    protected UUID givenProduct(String name, boolean published) {
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            on conflict do nothing
            """, category, "verano-" + category);
        UUID product = UUID.randomUUID();
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now(), now())
            """, product, category, name, name.toLowerCase(Locale.ROOT), published);
        return product;
    }

    @Override
    protected UUID givenVariant(UUID product, String size, int position, boolean active, int available) {
        UUID variant = UUID.randomUUID();
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, ?, 'Rosa', 0, 2000000, ?, now(), now())
            """, variant, product, "PP-" + variant.toString().substring(0, 8), size, position, active);
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variant,
            available);
        return variant;
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
    }

    /** Each read in its own transaction, as a use case would run it. */
    record InTransaction(CatalogModuleFigures adapter, TransactionTemplate transactions) implements CatalogFigures {

        private <R> R in(Supplier<R> work) {
            return transactions.execute(status -> work.get());
        }

        private long count(Supplier<Long> work) {
            Long count = in(work);
            return count == null ? 0 : count;
        }

        @Override
        public List<LowStockVariant> lowStock(int threshold, int limit) {
            return in(() -> adapter.lowStock(threshold, limit));
        }

        @Override
        public long lowStockCount(int threshold) {
            return count(() -> adapter.lowStockCount(threshold));
        }

        @Override
        public long publishedProducts() {
            return count(adapter::publishedProducts);
        }

        @Override
        public Map<UUID, ProductOf> productsOf(Collection<UUID> variantIds) {
            return in(() -> adapter.productsOf(variantIds));
        }
    }
}
