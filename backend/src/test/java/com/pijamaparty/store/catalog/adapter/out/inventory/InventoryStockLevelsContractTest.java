package com.pijamaparty.store.catalog.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.OpenStockLevels;
import com.pijamaparty.store.catalog.application.OpenStockLevelsContractTest;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the inventory module's public API to PostgreSQL, each call in the transaction a use case would give it. */
@ApiTest
class InventoryStockLevelsContractTest
    extends OpenStockLevelsContractTest<InventoryStockLevelsContractTest.InTransaction> {

    private final InventoryStockLevels adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    InventoryStockLevelsContractTest(InventoryStockLevels adapter, PlatformTransactionManager transactionManager,
                                     PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return new InTransaction(adapter, transactions);
    }

    @Override
    protected Optional<Integer> onHandOf(VariantId variant) {
        return owner.queryForList("select on_hand from stock_level where variant_id = ?", Integer.class,
            variant.value()).stream().findFirst();
    }

    @Override
    protected void givenOnHand(VariantId variant, int quantity) {
        owner.update("update stock_level set on_hand = ? where variant_id = ?", quantity, variant.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    @Test
    void givenNoTransaction_whenOpening_thenItIsRefused() {
        assertThatThrownBy(() -> adapter.open(List.of(VariantId.of(UUID.randomUUID()))))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Stock levels are opened in the transaction that creates the variants. */
    record InTransaction(InventoryStockLevels adapter, TransactionTemplate transactions) implements OpenStockLevels {

        @Override
        public void open(List<VariantId> variants) {
            transactions.executeWithoutResult(status -> adapter.open(variants));
        }
    }
}
