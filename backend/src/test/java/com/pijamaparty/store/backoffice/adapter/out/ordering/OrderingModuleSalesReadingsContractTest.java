package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.SalesReadings;
import com.pijamaparty.store.backoffice.application.SalesReadingsContractTest;
import com.pijamaparty.store.backoffice.domain.DateRange;
import com.pijamaparty.store.backoffice.domain.SalesTotal;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
 * Through the public API of the ordering module to {@code orders} and {@code order_item}, read by the administrator in
 * its own transaction, so Row Level Security applies as in production (RNF-27). Orders stored as the owner.
 */
@ApiTest
class OrderingModuleSalesReadingsContractTest extends SalesReadingsContractTest<SalesReadings> {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), md5(random()::text) || md5(random()::text), ?, 'Ana', 'Pérez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                ?, 0, 0, ?, 'MERCADOPAGO', '2026-10', ?, ?, ?)
        """;

    private static final String INSERT_ITEM = """
        insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        values (?, ?, 0, ?, 'PP-TEST', 'Pijama', 'M', 'Rosa', ?, ?)
        """;

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    OrderingModuleSalesReadingsContractTest(OrderingModuleSalesReadings adapter,
                                            PlatformTransactionManager transactionManager,
                                            PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected SalesReadings createSubject() {
        return asAdmin;
    }

    @Override
    protected void givenOrder(boolean sale, Instant createdAt, long totalCents, UUID variant, int units,
                              long unitCents) {
        UUID id = UUID.randomUUID();
        var at = createdAt.atOffset(ZoneOffset.UTC);
        owner.update(INSERT_ORDER, id, sale ? "PAID" : "CANCELLED", totalCents, totalCents,
            createdAt.plusSeconds(30 * 60).atOffset(ZoneOffset.UTC), at, at);
        owner.update(INSERT_ITEM, UUID.randomUUID(), id, variant, unitCents, units);
    }

    // Orders other API tests left behind would count too.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    /** Every read as the administrator, in its own transaction. */
    record AsAdmin(OrderingModuleSalesReadings adapter, TransactionTemplate transactions) implements SalesReadings {

        private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

        private <R> R as(Supplier<R> work) {
            return CurrentActor.callAs(ADMIN, () -> transactions.execute(status -> work.get()));
        }

        @Override
        public SalesTotal total(DateRange range) {
            return as(() -> adapter.total(range));
        }

        @Override
        public Map<LocalDate, SalesTotal> perDay(DateRange range) {
            return as(() -> adapter.perDay(range));
        }

        @Override
        public Map<YearMonth, SalesTotal> perMonth(DateRange range) {
            return as(() -> adapter.perMonth(range));
        }

        @Override
        public List<VariantSold> byVariant(DateRange range) {
            return as(() -> adapter.byVariant(range));
        }
    }
}
