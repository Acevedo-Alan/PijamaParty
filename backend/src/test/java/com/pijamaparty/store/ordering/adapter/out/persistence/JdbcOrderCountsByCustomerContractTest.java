package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OrderCountsByCustomer;
import com.pijamaparty.store.ordering.application.OrderCountsByCustomerContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
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
 * Orders stored by the owner role (no policies); each count read in its own transaction as the administrator, so Row
 * Level Security applies as in production (V14, RNF-27).
 */
@ApiTest
class JdbcOrderCountsByCustomerContractTest
    extends OrderCountsByCustomerContractTest<JdbcOrderCountsByCustomerContractTest.AsAdmin> {

    private static final Instant NOON = Instant.parse("2026-10-15T15:00:00Z");
    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at,
                            shipment_tracking_number, delivered_at)
        values (?, nextval('order_number_seq'), ?, md5(random()::text) || md5(random()::text), ?, 'Ana', 'Pérez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                2000000, 0, 300000, 2300000, 'MERCADOPAGO', '2026-10', ?, ?, ?, ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    JdbcOrderCountsByCustomerContractTest(JdbcOrderCountsByCustomer adapter,
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
    protected void givenOrder(Optional<UUID> customer, String status) {
        var at = NOON.atOffset(ZoneOffset.UTC);
        boolean shipped = status.equals("SHIPPED") || status.equals("DELIVERED");
        owner.update(INSERT_ORDER, UUID.randomUUID(), customer.orElse(null), status, at.plusMinutes(30), at, at,
            shipped ? "CA123456789AR" : null, status.equals("DELIVERED") ? at : null);
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(JdbcOrderCountsByCustomer adapter, TransactionTemplate transactions)
        implements OrderCountsByCustomer {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        @Override
        public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> adapter.countsOf(customerIds)));
        }
    }
}
