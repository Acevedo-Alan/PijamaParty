package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.application.AdminOrderViews;
import com.pijamaparty.store.ordering.application.AdminOrdersContractTest;
import com.pijamaparty.store.ordering.application.LatePayments;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.OrderPages;
import com.pijamaparty.store.ordering.application.OrderStatuses;
import com.pijamaparty.store.ordering.application.OrderSummary;
import com.pijamaparty.store.ordering.application.OrderTransitions;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.application.ShipmentTrackingNumbers;
import com.pijamaparty.store.ordering.application.StatusChanges;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Orders placed by their buyers, paid or expired by the system, and listed, viewed and changed by the administrator,
 * each call in its own committed transaction, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class JdbcAdminOrdersContractTest extends AdminOrdersContractTest<JdbcAdminOrdersContractTest.AsActors> {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final AsActors asActors;
    private final JdbcTemplate owner;
    private final RlsPolicyTester rls;

    JdbcAdminOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                                JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.asActors = new AsActors(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>());
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsActors createSubject() {
        return asActors;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asActors.tokens().put(token.hash(), token);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAGuest_whenListingOrViewingTheOrders_thenRowLevelSecurityHidesThem() {
        var order = placed(NOW, Optional.empty());
        Actor guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(asActors.as(guest, () -> asActors.adapter().page(Optional.empty(), 0, 10))).isEmpty();
        assertThat(asActors.as(guest, () -> asActors.adapter().adminViewOf(order.id()))).isEmpty();
    }

    @Test
    void givenAGuest_whenChangingAnOrder_thenRowLevelSecurityLeavesItAsItIs() {
        var order = placed(NOW, Optional.empty());
        Actor guest = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(asActors.as(guest, () -> asActors.adapter().change(order.id(), new StatusChange(
            OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, Optional.empty(), StatusChange.StockEffect.NONE),
            NOW))).isFalse();
        assertThat(OrderRows.statusOf(owner, order.id())).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAnOrderPaidAfterClosing_whenTheAdministratorTriesToSetOrClearTheMark_thenTheDatabaseRefusesIt() {
        var order = placed(NOW, Optional.empty());
        subject.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW);
        String mark = "update orders set paid_after_closing_at = now() where id = ?";
        String clear = "update orders set paid_after_closing_at = null where id = ?";

        assertThatThrownBy(() -> rls.as(ADMIN, sql -> sql.update(mark, order.id().value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("only the system");
        subject.paidAfterClosing(order.id(), NOW);
        assertThatThrownBy(() -> rls.as(ADMIN, sql -> sql.update(clear, order.id().value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("only the system");
        assertThatThrownBy(() -> rls.as(Actor.system(), sql -> sql.update(clear, order.id().value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("only the system");

        assertThat(subject.adminViewOf(order.id()).map(AdminOrder::paidAfterClosing)).contains(true);
    }

    @Test
    void givenACustomersOwnOrder_whenTheCustomerChangesItsStatusOrTrackingNumber_thenRowLevelSecurityLeavesItAsItIs() {
        UserId customer = UserId.of(UUID.randomUUID());
        var order = placed(NOW, Optional.empty());
        owner.update("update orders set user_id = ? where id = ?", customer.value(), order.id().value());
        Actor asCustomer = Actor.customer(customer, Optional.empty(), Optional.empty());

        assertThat(asActors.as(asCustomer, () -> asActors.adapter().change(order.id(), new StatusChange(
            OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, Optional.empty(), StatusChange.StockEffect.NONE),
            NOW))).isFalse();
        assertThat(asActors.as(asCustomer, () -> asActors.adapter().correct(order.id(),
            new ShipmentTrackingNumber("CA1"), NOW))).isFalse();
        assertThat(OrderRows.statusOf(owner, order.id())).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAnOrderStillOpen_whenTheOwnerMarksItPaidAfterClosing_thenTheDatabaseRefusesIt() {
        var order = placed(NOW, Optional.empty());

        assertThatThrownBy(() -> owner.update("update orders set paid_after_closing_at = now() where id = ?",
            order.id().value())).isInstanceOf(DataAccessException.class);
    }

    /**
     * Inserts as the order's buyer; pays, expires and marks late payments as the system; everything else as the
     * administrator.
     */
    record AsActors(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens)
        implements SaveOrders, OrderNumbers, OrderPages, AdminOrderViews, OrderStatuses, OrderTransitions,
        StatusChanges, ShipmentTrackingNumbers, LatePayments {

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            as(Actor.anonymous(Optional.empty(), Optional.of(token)), () -> {
                adapter.insert(order);
                return null;
            });
        }

        @Override
        public long next() {
            Long number = as(Actor.anonymous(Optional.empty(), Optional.empty()), adapter::next);
            return number == null ? 0 : number;
        }

        @Override
        public List<OrderSummary> page(Optional<OrderStatus> status, int offset, int limit) {
            return as(ADMIN, () -> adapter.page(status, offset, limit));
        }

        @Override
        public Optional<AdminOrder> adminViewOf(OrderId order) {
            return as(ADMIN, () -> adapter.adminViewOf(order));
        }

        @Override
        public Optional<OrderStatus> lockStatus(OrderId order) {
            return as(ADMIN, () -> adapter.lockStatus(order));
        }

        @Override
        public boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at) {
            return Boolean.TRUE.equals(as(Actor.system(), () -> adapter.transition(order, from, to, at)));
        }

        @Override
        public boolean change(OrderId order, StatusChange change, Instant at) {
            return Boolean.TRUE.equals(as(ADMIN, () -> adapter.change(order, change, at)));
        }

        @Override
        public boolean correct(OrderId order, ShipmentTrackingNumber number, Instant at) {
            return Boolean.TRUE.equals(as(ADMIN, () -> adapter.correct(order, number, at)));
        }

        @Override
        public void paidAfterClosing(OrderId order, Instant at) {
            as(Actor.system(), () -> {
                adapter.paidAfterClosing(order, at);
                return null;
            });
        }
    }
}
