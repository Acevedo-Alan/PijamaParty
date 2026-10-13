package com.pijamaparty.store.ordering.adapter.out.inventory;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.ReserveOrderStock;
import com.pijamaparty.store.ordering.application.ReserveOrderStockContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the public API of the inventory module to its tables in PostgreSQL, each call in its own transaction. */
@ApiTest
class InventoryModuleOrderStockContractTest extends ReserveOrderStockContractTest<ReserveOrderStock> {

    private final InventoryModuleOrderStock adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    InventoryModuleOrderStockContractTest(InventoryModuleOrderStock adapter,
                                          PlatformTransactionManager transactionManager,
                                          PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected ReserveOrderStock createSubject() {
        return (order, lines) -> transactions.execute(status -> adapter.reserve(order, lines));
    }

    @Override
    protected void givenStock(UUID variantId, int onHand) {
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            onHand);
    }

    @Override
    protected int availableOf(UUID variantId) {
        Integer available = owner.queryForObject(
            "select coalesce(sum(on_hand - reserved), 0) from stock_level where variant_id = ?", Integer.class,
            variantId);
        return available == null ? 0 : available;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }
}
