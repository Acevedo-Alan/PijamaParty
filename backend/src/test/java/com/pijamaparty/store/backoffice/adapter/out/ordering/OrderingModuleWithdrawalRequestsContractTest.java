package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.WithdrawalRequests;
import com.pijamaparty.store.backoffice.application.WithdrawalRequestsContractTest;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the ordering module to {@code withdrawal_request}, read by the administrator in its own
 * transaction, so Row Level Security applies as in production (RNF-27). Orders and requests stored as the owner.
 */
@ApiTest
class OrderingModuleWithdrawalRequestsContractTest extends WithdrawalRequestsContractTest<WithdrawalRequests> {

    private final OrderingModuleWithdrawalRequests adapter;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    OrderingModuleWithdrawalRequestsContractTest(OrderingModuleWithdrawalRequests adapter,
                                                 PlatformTransactionManager transactionManager,
                                                 PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected WithdrawalRequests createSubject() {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        return orderId -> CurrentActor.callAs(admin, () -> transactions.execute(status -> adapter.activeOf(orderId)));
    }

    @Override
    protected UUID givenRequested(String code, boolean outOfTerm) {
        UUID order = givenNoRequest();
        owner.update("insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at) "
                + "values (?, ?, ?, 'RECEIVED', ?, ?)", UUID.randomUUID(), order, code, outOfTerm,
            REQUESTED_AT.atOffset(ZoneOffset.UTC));
        return order;
    }

    @Override
    protected UUID givenResolved(String code) {
        UUID order = givenRequested(code, false);
        owner.update("update withdrawal_request set status = 'RESOLVED', resolved_at = ? where order_id = ?",
            REQUESTED_AT.plusSeconds(3600).atOffset(ZoneOffset.UTC), order);
        return order;
    }

    @Override
    protected UUID givenNoRequest() {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, REQUESTED_AT.minusSeconds(86_400));
        return order.value();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }
}
