package com.pijamaparty.store.ordering.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.application.StockDeductions;
import com.pijamaparty.store.ordering.application.StockDeductionsContractTest;
import com.pijamaparty.store.ordering.application.StockReleases;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the public API of the inventory module to its tables in PostgreSQL, each call in its own transaction. */
@ApiTest
class InventoryModuleStockDeductionsContractTest
    extends StockDeductionsContractTest<InventoryModuleStockDeductionsContractTest.InTransaction> {

    private final InventoryModuleStockDeductions deductions;
    private final InventoryModuleStockReleases releases;
    private final StockReservations reservations;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    InventoryModuleStockDeductionsContractTest(InventoryModuleStockDeductions deductions,
                                               InventoryModuleStockReleases releases,
                                               StockReservations reservations,
                                               PlatformTransactionManager transactionManager,
                                               PostgreSQLContainer<?> postgres) {
        this.deductions = deductions;
        this.releases = releases;
        this.reservations = reservations;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return new InTransaction(deductions, releases, transactions);
    }

    @Override
    protected void givenReserved(OrderId order, UUID variantId, int onHand, int reserved) {
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            onHand);
        var result = transactions.execute(status -> reservations.reserve(order.value(),
            List.of(new StockReservations.StockRequest(variantId, reserved))));
        assertThat(result).isEqualTo(new StockReservations.ReservationResult.Reserved());
    }

    @Override
    protected int availableOf(UUID variantId) {
        Integer available = owner.queryForObject(
            "select available from v_variant_availability where variant_id = ?", Integer.class, variantId);
        return available == null ? 0 : available;
    }

    @Override
    protected int onHandOf(UUID variantId) {
        Integer onHand = owner.queryForObject("select on_hand from stock_level where variant_id = ?", Integer.class,
            variantId);
        return onHand == null ? 0 : onHand;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    record InTransaction(StockDeductions deductions, StockReleases releases, TransactionTemplate transactions)
        implements StockDeductions, StockReleases {

        @Override
        public void deduct(OrderId order) {
            transactions.executeWithoutResult(status -> deductions.deduct(order));
        }

        @Override
        public void release(OrderId order) {
            transactions.executeWithoutResult(status -> releases.release(order));
        }
    }
}
