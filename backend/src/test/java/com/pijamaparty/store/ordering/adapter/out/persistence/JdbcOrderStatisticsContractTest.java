package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.ordering.api.SalesFigures.DayTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.MonthTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.Total;
import com.pijamaparty.store.ordering.api.SalesFigures.VariantSales;
import com.pijamaparty.store.ordering.application.OrderStatisticsContractTest;
import com.pijamaparty.store.ordering.application.SalesStatistics;
import com.pijamaparty.store.ordering.application.WorkloadStatistics;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
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
 * Orders and withdrawal requests stored as the owner and read by the administrator, each call in its own transaction,
 * so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class JdbcOrderStatisticsContractTest extends OrderStatisticsContractTest<JdbcOrderStatisticsContractTest.AsAdmin> {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at,
                            shipment_tracking_number, delivered_at)
        values (?, nextval('order_number_seq'), md5(random()::text) || md5(random()::text), ?, 'Ana', 'Pérez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                ?, 0, 0, ?, 'MERCADOPAGO', '2026-10', ?, ?, ?, ?, ?)
        """;

    private static final String INSERT_ITEM = """
        insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        values (?, ?, ?, ?, 'PP-TEST', 'Pijama', 'M', 'Rosa', ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    JdbcOrderStatisticsContractTest(JdbcOrderStatistics adapter, PlatformTransactionManager transactionManager,
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
    protected UUID givenOrder(OrderStatus status, Instant createdAt, Money total, List<Line> lines) {
        UUID id = UUID.randomUUID();
        var at = createdAt.atOffset(ZoneOffset.UTC);
        boolean shipped = status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED;
        owner.update(INSERT_ORDER, id, status.name(), total.cents(), total.cents(),
            createdAt.plusSeconds(30 * 60).atOffset(ZoneOffset.UTC), at, at, shipped ? "CA123456789AR" : null,
            status == OrderStatus.DELIVERED ? at : null);
        for (int position = 0; position < lines.size(); position++) {
            Line line = lines.get(position);
            owner.update(INSERT_ITEM, UUID.randomUUID(), id, position, line.variantId(), line.unitPrice().cents(),
                line.quantity());
        }
        return id;
    }

    @Override
    protected void givenWithdrawal(UUID order, boolean received) {
        var at = NOON.atOffset(ZoneOffset.UTC);
        owner.update("insert into withdrawal_request (id, order_id, code, status, out_of_term, created_at, resolved_at) "
                + "values (?, ?, ?, ?, false, ?, ?)", UUID.randomUUID(), order,
            code(),
            received ? "RECEIVED" : "RESOLVED", at, received ? null : at);
    }

    private static final String CODE_CHARACTERS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** A withdrawal code of the allowed characters (WithdrawalCode). */
    private static String code() {
        StringBuilder code = new StringBuilder("ARR-");
        for (int i = 0; i < 8; i++) {
            code.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    // Orders other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(JdbcOrderStatistics adapter, TransactionTemplate transactions)
        implements SalesStatistics, WorkloadStatistics {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        private <R> R as(Supplier<R> work) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> work.get()));
        }

        @Override
        public Total totals(Instant from, Instant to) {
            return as(() -> adapter.totals(from, to));
        }

        @Override
        public List<DayTotal> perDay(Instant from, Instant to) {
            return as(() -> adapter.perDay(from, to));
        }

        @Override
        public List<MonthTotal> perMonth(Instant from, Instant to) {
            return as(() -> adapter.perMonth(from, to));
        }

        @Override
        public List<VariantSales> byVariant(Instant from, Instant to) {
            return as(() -> adapter.byVariant(from, to));
        }

        @Override
        public long receivedWithdrawals() {
            Long count = as(adapter::receivedWithdrawals);
            return count == null ? 0 : count;
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
