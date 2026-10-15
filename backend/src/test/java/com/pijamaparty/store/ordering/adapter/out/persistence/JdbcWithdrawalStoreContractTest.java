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
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
 * Orders stored as the owner; matched, locked and given requests as a guest, each call in its own committed
 * transaction that first matches the order's number and email, as the withdrawal form runs, so Row Level Security
 * applies as in production (RNF-27, V26). Notices read as the system, as the email job does. The policies for every
 * actor come in T-E3-27.
 */
@ApiTest
class JdbcWithdrawalStoreContractTest extends WithdrawalStoreContractTest<JdbcWithdrawalStoreContractTest.AsGuest> {

    private static final Actor GUEST = Actor.anonymous(Optional.empty(), Optional.empty());

    private final AsGuest asGuest;
    private final JdbcTemplate owner;

    JdbcWithdrawalStoreContractTest(JdbcWithdrawalStore adapter, PlatformTransactionManager transactionManager,
                                    PostgreSQLContainer<?> postgres) {
        this.asGuest = new AsGuest(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsGuest createSubject() {
        return asGuest;
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
        long number = OrderRows.numberOf(owner, order);
        asGuest.numbers().put(order, number);
        return new WithdrawableOrder(order, number, status, createdAt, deliveredAt);
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

        asGuest.inOneTransaction(() -> {
            assertThat(asGuest.adapter().lock(order.number(), EMAIL)).isPresent();
            assertThatThrownBy(() -> owner.queryForList(
                "select id from orders where id = ? for update nowait", order.id().value()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("could not obtain lock");
            return null;
        });
    }

    @Test
    void givenAnOrderWithAnActiveRequest_whenASecondOneIsInsertedWithAnotherCode_thenTheDatabaseRefusesIt() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        asGuest.insert(new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-7K2M9QXZ"), false,
            NOW));

        assertThatThrownBy(() -> asGuest.insert(new NewWithdrawal(UUID.randomUUID(), order.id(),
            new WithdrawalCode("ARR-22222222"), false, NOW)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasStackTraceContaining("withdrawal_request_active_order_uk");
    }

    @Test
    void givenAGuestWithTheWrongEmail_whenItReadsOrRecordsARequestOfTheOrder_thenRowLevelSecurityRefusesIt() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        asGuest.insert(new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-7K2M9QXZ"), false,
            NOW));

        asGuest.inOneTransaction(() -> {
            assertThat(asGuest.adapter().lock(order.number(), "otra@example.com")).isEmpty();
            assertThat(asGuest.adapter().codeOf(order.id())).isEmpty();
            return null;
        });
        assertThatThrownBy(() -> asGuest.inOneTransaction(() -> {
            asGuest.adapter().lock(order.number(), "otra@example.com");
            return asGuest.adapter().insert(new NewWithdrawal(UUID.randomUUID(), order.id(),
                new WithdrawalCode("ARR-22222222"), false, NOW));
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenAGuestThatMatchedOneOrder_whenItRecordsARequestOfAnother_thenRowLevelSecurityRefusesIt() {
        WithdrawableOrder matched = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        WithdrawableOrder other = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());

        assertThatThrownBy(() -> asGuest.inOneTransaction(() -> {
            asGuest.adapter().lock(matched.number(), EMAIL);
            return asGuest.adapter().insert(new NewWithdrawal(UUID.randomUUID(), other.id(),
                new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW));
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenTheSystem_whenItLooksAnOrderUpByNumberAndEmail_thenItGetsNothing() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());

        Optional<WithdrawableOrder> found = CurrentActor.callAsSystem(() -> asGuest.transactions().execute(status ->
            asGuest.adapter().lock(order.number(), EMAIL)));

        assertThat(found).isEmpty();
    }

    /**
     * Every call as a guest in its own committed transaction, which first matches the order by its number and
     * {@link #EMAIL}, as the form does; notices as the system.
     */
    record AsGuest(JdbcWithdrawalStore adapter, TransactionTemplate transactions, Map<OrderId, Long> numbers)
        implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails {

        <T> T inOneTransaction(Supplier<T> block) {
            return CurrentActor.callAs(GUEST, () -> transactions.execute(status -> block.get()));
        }

        @Override
        public Optional<WithdrawableOrder> lock(long number, String email) {
            return inOneTransaction(() -> adapter.lock(number, email));
        }

        @Override
        public Optional<WithdrawalCode> codeOf(OrderId order) {
            return inOneTransaction(() -> {
                adapter.lock(numbers.get(order), EMAIL);
                return adapter.codeOf(order);
            });
        }

        @Override
        public boolean insert(NewWithdrawal withdrawal) {
            return Boolean.TRUE.equals(inOneTransaction(() -> {
                adapter.lock(numbers.get(withdrawal.order()), EMAIL);
                return adapter.insert(withdrawal);
            }));
        }

        @Override
        public Optional<WithdrawalNotice> noticeOf(UUID requestId) {
            return CurrentActor.callAsSystem(() -> transactions.execute(status -> adapter.noticeOf(requestId)));
        }
    }
}
