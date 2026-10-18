package com.pijamaparty.store.reviews.adapter.out.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.PurchasedProducts;
import com.pijamaparty.store.reviews.application.PurchasedProductsContractTest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of ordering to its tables in PostgreSQL, asked as the customer itself, so Row Level Security
 * applies as in production (RNF-27).
 */
@ApiTest
class OrderingPurchasedProductsContractTest
    extends PurchasedProductsContractTest<OrderingPurchasedProductsContractTest.AsCustomer> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderingPurchasedProducts adapter;
    private final JdbcTemplate owner;

    OrderingPurchasedProductsContractTest(OrderingPurchasedProducts adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsCustomer createSubject() {
        return new AsCustomer(adapter);
    }

    @Override
    protected void givenOrder(UserId customer, String status, UUID variant) {
        UUID order = UUID.randomUUID();
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        boolean shipped = status.equals("SHIPPED") || status.equals("DELIVERED");
        owner.update("""
            insert into orders (id, number, user_id, tracking_token_hash, status, customer_name, customer_email,
                                customer_phone, national_id, street, street_number, city, province, postal_code,
                                subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                                terms_version, reservation_expires_at, shipment_tracking_number, delivered_at,
                                created_at, updated_at)
            values (?, nextval('order_number_seq'), ?, ?, ?, 'Ana Pérez', 'ana@example.com', '3425551234',
                    '30123456', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 1500000, 0, 300000, 1800000,
                    'MERCADOPAGO', '2026-10', now() + interval '30 minutes', ?,
                    case when ? then now() end, now(), now())
            """, order, customer.value(), HexFormat.of().formatHex(token), status,
            shipped ? "TN123456789AR" : null, status.equals("DELIVERED"));
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (?, ?, 0, ?, 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 1500000, 1)
            """, UUID.randomUUID(), order, variant);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAnotherCustomersPaidOrder_whenAskedAsThisCustomerForTheOther_thenRowLevelSecurityHidesIt() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId bruno = UserId.of(UUID.randomUUID());
        UUID variant = UUID.randomUUID();
        givenOrder(bruno, "PAID", variant);

        assertThat(CurrentActor.callAs(Actor.customer(ana, Optional.empty(), Optional.empty()),
            () -> adapter.boughtAnyOf(bruno, Set.of(variant)))).isFalse();
    }

    /** Asks as {@code customer}, in the short read-only transaction of the ordering API. */
    record AsCustomer(OrderingPurchasedProducts adapter) implements PurchasedProducts {

        @Override
        public boolean boughtAnyOf(UserId customer, Set<UUID> variantIds) {
            return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
                () -> adapter.boughtAnyOf(customer, variantIds));
        }
    }
}
