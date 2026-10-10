package com.pijamaparty.store.inventory.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.application.AddStockLevels;
import com.pijamaparty.store.inventory.application.AddStockLevelsContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Each call committed in its own transaction, then read back from PostgreSQL. */
@ApiTest
class JdbcStockLevelStoreContractTest
    extends AddStockLevelsContractTest<JdbcStockLevelStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcStockLevelStoreContractTest(JdbcStockLevelStore adapter, PlatformTransactionManager transactionManager,
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
    protected Optional<Level> levelOf(UUID variantId) {
        return owner.query("select on_hand, updated_at from stock_level where variant_id = ?",
            (row, n) -> new Level(row.getInt("on_hand"), row.getObject("updated_at", OffsetDateTime.class).toInstant()),
            variantId).stream().findFirst();
    }

    @Override
    protected void givenOnHand(UUID variantId, int quantity) {
        owner.update("update stock_level set on_hand = ? where variant_id = ?", quantity, variantId);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    record InTransaction(JdbcStockLevelStore adapter, TransactionTemplate transactions) implements AddStockLevels {

        @Override
        public void addEmpty(Collection<UUID> variantIds, Instant at) {
            transactions.executeWithoutResult(status -> adapter.addEmpty(variantIds, at));
        }
    }
}
