package com.pijamaparty.store.ordering.adapter.in.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.accounts.api.CustomerDataContributorContractTest;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The orders contributor of the data download, through the real use case and adapter: orders stored by the owner
 * role, exported in a read-only transaction as the customer, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class OrdersCustomerDataContractTest extends CustomerDataContributorContractTest<OrdersCustomerData> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrdersCustomerData contributor;
    private final TransactionTemplate readOnly;
    private final JdbcTemplate owner;

    OrdersCustomerDataContractTest(OrdersCustomerData contributor, PlatformTransactionManager transactionManager,
                                   PostgreSQLContainer<?> postgres) {
        this.contributor = contributor;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OrdersCustomerData createSubject() {
        return contributor;
    }

    @Override
    protected CustomerDataSection expectedSection() {
        return CustomerDataSection.ORDERS;
    }

    @Override
    protected UserId customerWith(int records) {
        UserId customer = UserId.of(UUID.randomUUID());
        for (int n = 0; n < records; n++) {
            order(customer);
        }
        return customer;
    }

    @Override
    protected List<?> exportAs(UserId customer, Supplier<List<?>> export) {
        return CurrentActor.callAs(Actor.customer(customer, Optional.empty(), Optional.empty()),
            () -> readOnly.execute(status -> export.get()));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenAnotherCustomersOrders_whenExportingThemAsThisCustomer_thenRowLevelSecurityHidesThem() {
        UserId ana = customerWith(0);
        UserId bruno = customerWith(2);

        assertThat(exportAs(ana, () -> subject.export(bruno))).isEmpty();
    }

    @Test
    void givenAnOrderWithTwoLines_whenExporting_thenItCarriesBothLinesAndTheBuyersCopy() {
        UserId ana = customerWith(1);

        List<?> exported = exportAs(ana, () -> subject.export(ana));

        assertThat(exported).singleElement().isInstanceOfSatisfying(OrdersCustomerData.OrderRecord.class, order -> {
            assertThat(order.items()).extracting(OrdersCustomerData.ItemRecord::sku)
                .containsExactly("PL-M-ROSA", "CA-S-AZUL");
            assertThat(order.buyer().nationalId()).isEqualTo("30123456");
            assertThat(order.totalCents()).isEqualTo(5_800_000);
        });
    }

    /** An order of the customer with two lines, as its checkout leaves it. */
    private void order(UserId customer) {
        UUID id = UUID.randomUUID();
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        owner.update("""
            insert into orders (id, number, user_id, tracking_token_hash, status, customer_first_name,
                                customer_last_name, customer_email,
                                customer_phone, national_id, street, street_number, city, province, postal_code,
                                subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                                terms_version, reservation_expires_at, created_at, updated_at)
            values (?, nextval('order_number_seq'), ?, ?, 'AWAITING_PAYMENT', 'Ana', 'Pérez', 'ana@example.com',
                    '3425551234', '30123456', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 5500000, 0,
                    300000, 5800000, 'MERCADOPAGO', '2026-10', now() + interval '30 minutes', now(), now())
            """, id, customer.value(), HexFormat.of().formatHex(token));
        owner.update("""
            insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color,
                                    unit_price_cents, quantity)
            values (?, ?, 0, ?, 'PL-M-ROSA', 'Pijama Luna', 'M', 'Rosa', 2000000, 2),
                   (?, ?, 1, ?, 'CA-S-AZUL', 'Camisón Sol', 'S', 'Azul', 1500000, 1)
            """, UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID(), id, UUID.randomUUID());
    }
}
