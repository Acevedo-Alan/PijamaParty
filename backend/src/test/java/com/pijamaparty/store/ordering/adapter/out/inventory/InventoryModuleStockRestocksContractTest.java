package com.pijamaparty.store.ordering.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.application.StockRestocks;
import com.pijamaparty.store.ordering.application.StockRestocksContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the inventory module to its tables in PostgreSQL, each call in its own transaction,
 * restocking as the administrator who cancels the order (RNF-27).
 */
@ApiTest
class InventoryModuleStockRestocksContractTest extends StockRestocksContractTest<StockRestocks> {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InventoryModuleStockRestocks adapter;
    private final StockReservations reservations;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    InventoryModuleStockRestocksContractTest(InventoryModuleStockRestocks adapter, StockReservations reservations,
                                             PlatformTransactionManager transactionManager,
                                             PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.reservations = reservations;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected StockRestocks createSubject() {
        return order -> CurrentActor.runAs(ADMIN,
            () -> transactions.executeWithoutResult(status -> adapter.restock(order)));
    }

    @Override
    protected void givenDeducted(OrderId order, UUID variantId, int onHand, int deducted) {
        reserved(order, variantId, onHand, deducted);
        transactions.executeWithoutResult(status -> reservations.deduct(order.value()));
    }

    @Override
    protected void givenReleased(OrderId order, UUID variantId, int onHand, int reserved) {
        reserved(order, variantId, onHand, reserved);
        transactions.executeWithoutResult(status -> reservations.release(order.value()));
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

    private void reserved(OrderId order, UUID variantId, int onHand, int reserved) {
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            onHand);
        var result = transactions.execute(status -> reservations.reserve(order.value(),
            List.of(new StockReservations.StockRequest(variantId, reserved))));
        assertThat(result).isEqualTo(new StockReservations.ReservationResult.Reserved());
    }
}
