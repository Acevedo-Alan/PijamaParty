package com.pijamaparty.store.inventory.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.application.AvailableStock;
import com.pijamaparty.store.inventory.application.LoadStockLevels;
import com.pijamaparty.store.inventory.application.SaveStockLevels;
import com.pijamaparty.store.inventory.application.StockHistory;
import com.pijamaparty.store.inventory.application.StockStoreContractTest;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.inventory.domain.StockLevel;
import com.pijamaparty.store.inventory.domain.StockMovement;
import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Each call committed in its own transaction, then read back from PostgreSQL. */
@ApiTest
class JdbcStockStoreContractTest extends StockStoreContractTest<JdbcStockStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcStockStoreContractTest(JdbcStockLevelStore adapter, PlatformTransactionManager transactionManager,
                               PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @Override
    protected void givenStockLevel(VariantId variantId, Instant at) {
        inTransaction.transactions().executeWithoutResult(
            status -> inTransaction.adapter().addEmpty(List.of(variantId.value()), at));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    record InTransaction(JdbcStockLevelStore adapter, TransactionTemplate transactions)
        implements LoadStockLevels, SaveStockLevels, StockHistory, AvailableStock {

        @Override
        public Optional<StockLevel> lockByVariantId(VariantId variantId) {
            return transactions.execute(status -> adapter.lockByVariantId(variantId));
        }

        @Override
        public Optional<StockMovement> findAdjustment(VariantId variantId, AdjustmentKey key) {
            return transactions.execute(status -> adapter.findAdjustment(variantId, key));
        }

        @Override
        public void save(StockLevel level) {
            transactions.executeWithoutResult(status -> adapter.save(level));
        }

        @Override
        public OptionalInt onHand(VariantId variantId) {
            return transactions.execute(status -> adapter.onHand(variantId));
        }

        @Override
        public List<StockMovement> movements(VariantId variantId, int offset, int limit) {
            return transactions.execute(status -> adapter.movements(variantId, offset, limit));
        }

        @Override
        public Map<VariantId, Integer> availableOf(Collection<VariantId> variantIds) {
            return transactions.execute(status -> adapter.availableOf(variantIds));
        }
    }
}
