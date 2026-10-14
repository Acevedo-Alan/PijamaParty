package com.pijamaparty.store.ordering.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.ReserveOrderStock;
import com.pijamaparty.store.ordering.application.ReserveOrderStockContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    /** T-E3-12 (UC-16): the second reservation waits for the first one's row lock, then sees it committed. */
    @Test
    void givenTheLastUnitHeldByAnOpenReservation_whenAnotherWaitsAndTheFirstCommits_thenTheOtherIsShort()
        throws Exception {
        UUID pijama = UUID.randomUUID();
        givenStock(pijama, 1);

        assertThat(secondWhileTheFirstHolds(pijama, true)).contains(new ReserveOrderStock.Shortage(pijama, 0));

        assertThat(availableOf(pijama)).isZero();
        assertThat(reservations()).isOne();
    }

    /** T-E3-12 (UC-16): a reservation is not refused for having waited; it gets the unit the first one gave back. */
    @Test
    void givenTheLastUnitHeldByAnOpenReservation_whenAnotherWaitsAndTheFirstRollsBack_thenTheOtherGetsIt()
        throws Exception {
        UUID pijama = UUID.randomUUID();
        givenStock(pijama, 1);

        assertThat(secondWhileTheFirstHolds(pijama, false)).isEmpty();

        assertThat(availableOf(pijama)).isZero();
        assertThat(reservations()).isOne();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    /**
     * The first order reserves the last unit and keeps its transaction open until the second order's reservation waits
     * on a lock; then the first commits or rolls back, and the second's outcome is returned.
     */
    private Optional<ReserveOrderStock.Shortage> secondWhileTheFirstHolds(UUID variantId, boolean commitFirst)
        throws Exception {
        var held = new CountDownLatch(1);
        var finishFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ReserveOrderStock.Shortage>> first = executor.submit(() -> transactions.execute(status -> {
                Optional<ReserveOrderStock.Shortage> outcome = adapter.reserve(OrderId.of(UUID.randomUUID()),
                    List.of(line(variantId, 1)));
                held.countDown();
                awaitQuietly(finishFirst);
                if (!commitFirst) {
                    status.setRollbackOnly();
                }
                return outcome;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Optional<ReserveOrderStock.Shortage>> second = executor.submit(() -> transactions.execute(status ->
                adapter.reserve(OrderId.of(UUID.randomUUID()), List.of(line(variantId, 1)))));
            awaitALockWaiter();
            finishFirst.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEmpty();
            return second.get(30, TimeUnit.SECONDS);
        } finally {
            finishFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitALockWaiter() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (owner.queryForObject("select count(*) from pg_locks where not granted", Integer.class) == 0) {
            assertThat(Instant.now()).as("the second reservation waits on the first one's lock").isBefore(deadline);
            Thread.sleep(20);
        }
    }

    private int reservations() {
        return owner.queryForObject("select count(*) from stock_movement where type = 'RESERVATION'", Integer.class);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the other reservation never started waiting");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
