package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.CustomerOrderHistories;
import com.pijamaparty.store.backoffice.application.CustomerOrderHistoriesContractTest;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
 * so Row Level Security applies as in production (V14, RNF-27). Orders stored as the owner, the guest's with the
 * customer's email.
 */
@ApiTest
class OrderingModuleCustomerOrderHistoriesContractTest
    extends CustomerOrderHistoriesContractTest<OrderingModuleCustomerOrderHistoriesContractTest.AsAdmin> {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at,
                            shipment_tracking_number, delivered_at)
        values (?, nextval('order_number_seq'), ?, md5(random()::text) || md5(random()::text), ?, 'Ana', 'Gómez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                ?, 0, 0, ?, 'MERCADOPAGO', '2026-10', ?, ?, ?, ?, ?)
        returning number
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    OrderingModuleCustomerOrderHistoriesContractTest(OrderingModuleCustomerOrderHistories adapter,
                                                     PlatformTransactionManager transactionManager,
                                                     PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdmin createSubject() {
        return asAdmin;
    }

    @Override
    protected OrderRow givenOrder(Optional<UUID> customer, Status status, long totalCents, Instant at) {
        UUID id = UUID.randomUUID();
        var placed = at.atOffset(ZoneOffset.UTC);
        boolean shipped = status == Status.SHIPPED || status == Status.DELIVERED;
        Long number = owner.queryForObject(INSERT_ORDER, Long.class, id, customer.orElse(null), status.name(),
            totalCents, totalCents, placed.plusMinutes(30), placed, placed, shipped ? "CA123456789AR" : null,
            status == Status.DELIVERED ? placed : null);
        return new OrderRow(id, number, at, status, Money.of(totalCents));
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(OrderingModuleCustomerOrderHistories adapter, TransactionTemplate transactions)
        implements CustomerOrderHistories {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public OrderHistory historyOf(UUID customerId, int page) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.historyOf(customerId,
                page)));
        }
    }
}
