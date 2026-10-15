package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.ActiveWithdrawals;
import com.pijamaparty.store.ordering.application.SaveWithdrawals;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.application.WithdrawalNoticeDetails;
import com.pijamaparty.store.ordering.application.WithdrawalStoreContractTest;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders stored as the owner; looked up, locked and given requests as the system, each call in its own committed
 * transaction, as the withdrawal button runs, so Row Level Security applies as in production (RNF-27). The policies
 * for every actor come in T-E3-27.
 */
@ApiTest
class JdbcWithdrawalStoreContractTest extends WithdrawalStoreContractTest<JdbcWithdrawalStoreContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcTemplate owner;

    JdbcWithdrawalStoreContractTest(JdbcWithdrawalStore adapter, PlatformTransactionManager transactionManager,
                                    PostgreSQLContainer<?> postgres) {
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected WithdrawableOrder placed(OrderStatus status, Instant createdAt, Optional<Instant> deliveredAt) {
        OrderId order = OrderId.of(UUID.randomUUID());
        OrderRows.insert(owner, order, OrderStatus.PAID, createdAt.plusSeconds(30 * 60));
        if (status == OrderStatus.DELIVERED) {
            owner.update("update orders set status = 'DELIVERED', shipment_tracking_number = 'CA123456789AR', "
                + "delivered_at = ? where id = ?", deliveredAt.orElseThrow().atOffset(ZoneOffset.UTC), order.value());
        } else {
            owner.update("update orders set status = ? where id = ?", status.name(), order.value());
        }
        return new WithdrawableOrder(order, OrderRows.numberOf(owner, order), "ana@example.com", status, createdAt,
            deliveredAt);
    }

    @Override
    protected void resolved(UUID request, Instant at) {
        owner.update("update withdrawal_request set status = 'RESOLVED', resolved_at = ? where id = ?",
            at.atOffset(ZoneOffset.UTC), request);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAnOrderLockedByAWithdrawal_whenAnotherTransactionLocksItWithoutWaiting_thenItIsHeld() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());

        CurrentActor.runAsSystem(() -> asSystem.transactions().executeWithoutResult(status -> {
            assertThat(asSystem.adapter().lock(order.number())).isPresent();
            assertThatThrownBy(() -> owner.queryForList(
                "select id from orders where id = ? for update nowait", order.id().value()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("could not obtain lock");
        }));
    }

    @Test
    void givenAnOrderWithAnActiveRequest_whenASecondOneIsInsertedWithAnotherCode_thenTheDatabaseRefusesIt() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        asSystem.insert(new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-7K2M9QXZ"), false,
            NOW));

        assertThatThrownBy(() -> asSystem.insert(new NewWithdrawal(UUID.randomUUID(), order.id(),
            new WithdrawalCode("ARR-22222222"), false, NOW)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasStackTraceContaining("withdrawal_request_active_order_uk");
    }

    /** Every call as the system, in its own committed transaction. */
    record AsSystem(JdbcWithdrawalStore adapter, TransactionTemplate transactions)
        implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails {

        @Override
        public Optional<WithdrawableOrder> lock(long number) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.lock(number)));
        }

        @Override
        public Optional<WithdrawalCode> codeOf(OrderId order) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.codeOf(order)));
        }

        @Override
        public boolean insert(NewWithdrawal withdrawal) {
            return Boolean.TRUE.equals(CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.insert(withdrawal))));
        }

        @Override
        public Optional<WithdrawalNotice> noticeOf(UUID requestId) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.noticeOf(requestId)));
        }
    }
}
