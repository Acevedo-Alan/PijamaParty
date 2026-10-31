package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.OrderCounts;
import com.pijamaparty.store.backoffice.application.OrderCountsContractTest;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the ordering module to {@code orders}, read by the administrator in its own transaction,
 * so Row Level Security applies as in production (RNF-27). Orders stored as the owner.
 */
@ApiTest
class OrderingModuleOrderCountsContractTest extends OrderCountsContractTest<OrderCounts> {

    private static final Instant EXPIRES_AT = Instant.parse("2026-10-31T15:30:00Z");

    private final OrderingModuleOrderCounts adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    OrderingModuleOrderCountsContractTest(OrderingModuleOrderCounts adapter,
                                          PlatformTransactionManager transactionManager,
                                          PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OrderCounts createSubject() {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        return () -> {
            Long count = CurrentActor.callAs(admin, () -> transactions.execute(status -> adapter.paidWaiting()));
            return count == null ? 0 : count;
        };
    }

    @Override
    protected void givenPaid() {
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PAID, EXPIRES_AT);
    }

    @Override
    protected void givenAwaitingPayment() {
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.AWAITING_PAYMENT, EXPIRES_AT);
    }

    @Override
    protected void givenPreparing() {
        OrderRows.insert(owner, OrderId.of(UUID.randomUUID()), OrderStatus.PREPARING, EXPIRES_AT);
    }

    // Orders other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }
}
