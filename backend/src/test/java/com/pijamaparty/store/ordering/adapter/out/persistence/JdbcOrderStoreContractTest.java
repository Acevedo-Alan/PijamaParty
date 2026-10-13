package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OrderStoreContractTest;
import com.pijamaparty.store.ordering.application.OrderTransitions;
import com.pijamaparty.store.ordering.application.OverdueOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
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

/** Each call committed in its own transaction as {@code SYSTEM}, as the expiry job runs (RNF-27). */
@ApiTest
class JdbcOrderStoreContractTest extends OrderStoreContractTest<JdbcOrderStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcOrderStoreContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                               PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager), Actor.system());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @Override
    protected void givenOrder(OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        OrderRows.insert(owner, order, status, reservationExpiresAt);
    }

    @Override
    protected Optional<OrderStatus> statusOf(OrderId order) {
        return OrderRows.statusOf(owner, order);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_item, orders");
    }

    @Test
    void givenTheOldestDueOrderLockedByAnotherRun_whenLookingForTheNextDue_thenTheNextOneIsFound() throws Exception {
        OrderId oldest = OrderId.of(UUID.randomUUID());
        OrderId next = OrderId.of(UUID.randomUUID());
        givenOrder(oldest, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(600));
        givenOrder(next, OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));
        var locked = new CountDownLatch(1);
        var done = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<OrderId>> first = executor.submit(() -> inTransaction.run(() -> {
                Optional<OrderId> found = inTransaction.adapter().lockNextDue(NOW);
                locked.countDown();
                awaitQuietly(done);
                return found;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(subject.lockNextDue(NOW)).contains(next);

            done.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).contains(oldest);
        } finally {
            done.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void givenAGuest_whenLookingForTheNextDue_thenRowLevelSecurityHidesEveryOrder() {
        givenOrder(OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT, NOW.minusSeconds(60));
        var guest = new InTransaction(inTransaction.adapter(), inTransaction.transactions(),
            Actor.anonymous(Optional.empty(), Optional.empty()));

        assertThat(guest.lockNextDue(NOW)).isEmpty();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the other transaction never finished");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record InTransaction(JdbcOrderStore adapter, TransactionTemplate transactions, Actor actor)
        implements OverdueOrders, OrderTransitions {

        <T> T run(java.util.function.Supplier<T> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }

        @Override
        public Optional<OrderId> lockNextDue(Instant now) {
            return run(() -> adapter.lockNextDue(now));
        }

        @Override
        public boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at) {
            return Boolean.TRUE.equals(run(() -> adapter.transition(order, from, to, at)));
        }
    }
}
