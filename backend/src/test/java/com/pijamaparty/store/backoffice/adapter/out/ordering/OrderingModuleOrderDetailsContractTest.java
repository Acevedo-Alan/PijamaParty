package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.OrderDetails;
import com.pijamaparty.store.backoffice.application.OrderDetailsContractTest;
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
class OrderingModuleOrderDetailsContractTest extends OrderDetailsContractTest<OrderDetails> {

    private final OrderingModuleOrderDetails adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    OrderingModuleOrderDetailsContractTest(OrderingModuleOrderDetails adapter,
                                           PlatformTransactionManager transactionManager,
                                           PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OrderDetails createSubject() {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        return orderId -> CurrentActor.callAs(admin, () -> transactions.execute(status -> adapter.find(orderId)));
    }

    @Override
    protected void givenOrderAwaitingPayment(UUID orderId, long number) {
        OrderRows.insert(owner, OrderId.of(orderId), OrderStatus.AWAITING_PAYMENT, Instant.now().plusSeconds(600));
        owner.update("update orders set number = ? where id = ?", number, orderId);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }
}
