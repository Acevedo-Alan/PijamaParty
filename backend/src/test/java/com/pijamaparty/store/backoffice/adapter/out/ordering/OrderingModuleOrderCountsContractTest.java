package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.OrderCounts;
import com.pijamaparty.store.backoffice.application.OrderCountsContractTest;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the ordering module to {@code orders} and {@code withdrawal_request}, read by the
 * administrator in its own transaction, so Row Level Security applies as in production (RNF-27). Orders stored as the
 * owner.
 */
@ApiTest
class OrderingModuleOrderCountsContractTest extends OrderCountsContractTest<OrderCounts> {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at,
                            shipment_tracking_number, delivered_at)
        values (?, nextval('order_number_seq'), md5(random()::text) || md5(random()::text), ?, 'Ana', 'Pérez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                2000000, 0, 300000, 2300000, 'MERCADOPAGO', '2026-10', ?, ?, ?, ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;
    private int withdrawals;

    OrderingModuleOrderCountsContractTest(OrderingModuleOrderCounts adapter,
                                          PlatformTransactionManager transactionManager,
                                          PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OrderCounts createSubject() {
        return asAdmin;
    }

    @Override
    protected UUID givenOrder(Status status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        var at = createdAt.atOffset(ZoneOffset.UTC);
        boolean shipped = status == Status.SHIPPED || status == Status.DELIVERED;
        owner.update(INSERT_ORDER, id, status.name(), createdAt.plusSeconds(30 * 60).atOffset(ZoneOffset.UTC), at, at,
            shipped ? "CA123456789AR" : null, status == Status.DELIVERED ? at : null);
        return id;
    }

    @Override
    protected void givenReceivedWithdrawal() {
        UUID order = givenOrder(Status.PAID, NOON);
        // Codes of the allowed characters (WithdrawalCode), one per request of this test.
        String code = "ARR-" + "AAAAAAA" + "23456789".charAt(withdrawals++);
        owner.update("insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at) "
            + "values (?, ?, ?, 'RECEIVED', false, ?)", UUID.randomUUID(), order, code, NOON.atOffset(ZoneOffset.UTC));
    }

    // Orders other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(OrderingModuleOrderCounts adapter, TransactionTemplate transactions) implements OrderCounts {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        private <R> R as(Supplier<R> work) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> work.get()));
        }

        private long count(Supplier<Long> work) {
            Long count = as(work);
            return count == null ? 0 : count;
        }

        @Override
        public long paidWaiting() {
            return count(adapter::paidWaiting);
        }

        @Override
        public long preparing() {
            return count(adapter::preparing);
        }

        @Override
        public long receivedWithdrawals() {
            return count(adapter::receivedWithdrawals);
        }

        @Override
        public List<RecentOrder> newest(int limit) {
            return as(() -> adapter.newest(limit));
        }

        @Override
        public Set<UUID> openAmong(Collection<UUID> orderIds) {
            return as(() -> adapter.openAmong(orderIds));
        }
    }
}
