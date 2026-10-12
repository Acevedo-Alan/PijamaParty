package com.pijamaparty.store.ordering.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-01: the ordering schema (V14) enforces the UC-13 to UC-18 rules, and its main Row Level Security policies
 * (RNF-27). The full policy matrix is T-E3-21.
 */
@ApiTest
class OrderingSchemaTest {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_name, customer_email,
                            customer_phone, national_id, street, street_number, floor, apartment, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents, coupon_id,
                            coupon_code, payment_provider, terms_version, reservation_expires_at,
                            shipment_tracking_number, created_at, updated_at, delivered_at)
        values (?, nextval('order_number_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                'MERCADOPAGO', '2026-10', now() + interval '30 minutes', ?, now(), now(), ?)
        """;
    private static final String INSERT_ITEM = """
        insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        values (?, ?, ?, ?, 'PP-ABCD2345', 'Pijama corto de algodón', 'M', 'Rosa', 2000000, 1)
        """;

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    OrderingSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                       PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_item, orders");
    }

    @Test
    void givenTheOrderTables_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        for (String table : List.of("orders", "order_item")) {
            var flags = owner.queryForMap(
                "select relrowsecurity, relforcerowsecurity from pg_class where oid = ?::regclass", table);
            assertThat(flags).as(table).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
    }

    @Test
    void givenACompleteOrder_whenStored_thenItIsAcceptedWithANumberFrom1001() {
        var id = store(Row.guest());

        assertThat(owner.queryForObject("select number from orders where id = ?", Long.class, id))
            .isGreaterThanOrEqualTo(1001);
    }

    @Test
    void givenBuyerDataOutsideTheUc13Formats_whenStored_thenItIsRejected() {
        rejects(row -> row.withPhone("+54 342 555-1234"), "orders_customer_phone_ck");
        rejects(row -> row.withPhone("1234567"), "orders_customer_phone_ck");
        rejects(row -> row.withNationalId("12.345.678"), "orders_national_id_ck");
        rejects(row -> row.withNationalId("123456"), "orders_national_id_ck");
        rejects(row -> row.withNationalId("123456789"), "orders_national_id_ck");
        rejects(row -> row.withEmail("Ana@Example.com"), "orders_customer_email_ck");
        rejects(row -> row.withName(" Ana"), "orders_customer_name_ck");
        rejects(row -> row.withName("x".repeat(101)), "orders_customer_name_ck");
        rejects(row -> row.withPostalCode("s3000abc"), "orders_postal_code_ck");
        rejects(row -> row.withPostalCode("300"), "orders_postal_code_ck");
        rejects(row -> row.withProvince("SANTA FE"), "orders_province_ck");
        rejects(row -> row.withFloor(""), "orders_floor_ck");
    }

    @Test
    void givenTheAcceptedFormats_whenStored_thenTheyAreAccepted() {
        store(Row.guest().withPostalCode("3000").withNationalId("1234567").withPhone("543425551234"));
        store(Row.guest().withTrackingHash(new TrackingToken("another").hash()).withPostalCode("S3000ABC")
            .withFloor(null));

        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(2);
    }

    @Test
    void givenAmountsThatDoNotAddUp_whenStored_thenTheyAreRejected() {
        rejects(row -> row.withAmounts(2_000_000, 0, 300_000, 2_000_000), "orders_total_ck");
        rejects(row -> row.withAmounts(2_000_000, 2_000_001, 0, -1), "orders_discount_ck");
        rejects(row -> row.withAmounts(0, 0, 0, 0), "orders_subtotal_ck");
    }

    @Test
    void givenACouponIdWithoutItsCode_whenStored_thenItIsRejected() {
        rejects(row -> row.withCoupon(UUID.randomUUID(), null), "orders_coupon_ck");
        rejects(row -> row.withCoupon(UUID.randomUUID(), "verano10"), "orders_coupon_code_format_ck");
    }

    @Test
    void givenAShippedOrDeliveredOrder_whenStoredWithoutTrackingNumberOrDeliveryDate_thenItIsRejected() {
        rejects(row -> row.withStatus("SHIPPED"), "orders_shipped_tracking_ck");
        rejects(row -> row.withStatus("DELIVERED").withShipmentTrackingNumber("AR123"), "orders_delivered_at_ck");
        rejects(row -> row.withStatus("ON_HOLD"), "orders_status_ck");
    }

    @Test
    void givenAnOrderWithAVariant_whenASecondLineOfItIsStored_thenItIsRejected() {
        var order = store(Row.guest());
        var variant = UUID.randomUUID();
        owner.update(INSERT_ITEM, UUID.randomUUID(), order, 0, variant);

        assertThatThrownBy(() -> owner.update(INSERT_ITEM, UUID.randomUUID(), order, 1, variant))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("order_item_order_variant_uk");
    }

    @Test
    void givenAStoredOrder_whenTheApplicationTriesToDeleteItOrChangeItsItems_thenItIsDenied() {
        var order = store(Row.guest());
        owner.update(INSERT_ITEM, UUID.randomUUID(), order, 0, UUID.randomUUID());

        for (String sql : List.of("delete from orders", "update order_item set quantity = 2", "delete from order_item")) {
            assertThatThrownBy(() -> rls.as(SYSTEM, jdbc -> jdbc.update(sql))).as(sql)
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        }
    }

    @Test
    void givenAGuest_whenItCreatesAnOrderBoundToItsTrackingToken_thenItAndItsItemsAreStoredAndOnlyItSeesThem() {
        var order = UUID.randomUUID();
        rls.as(GUEST, jdbc -> {
            jdbc.update(INSERT_ORDER, Row.guest().withId(order).params());
            return jdbc.update(INSERT_ITEM, UUID.randomUUID(), order, 0, UUID.randomUUID());
        });

        assertThat(rls.as(GUEST, this::orderIds)).containsExactly(order);
        assertThat(rls.as(GUEST, this::itemOrderIds)).containsExactly(order);
        assertThat(rls.as(CUSTOMER_A, this::orderIds)).isEmpty();
        assertThat(rls.as(CUSTOMER_A, this::itemOrderIds)).isEmpty();
        assertThat(rls.withoutContext(this::orderIds)).isEmpty();
    }

    @Test
    void givenAGuest_whenItCreatesAnOrderOfAnotherTokenOrOfACustomer_thenRowLevelSecurityRejectsIt() {
        var otherToken = Row.guest().withTrackingHash(new TrackingToken("someone-else").hash());
        var ofCustomer = Row.guest().withUser(CUSTOMER_A_ID.value());

        for (Row row : List.of(otherToken, ofCustomer, Row.guest().withStatus("PAID"))) {
            assertThatThrownBy(() -> rls.as(GUEST, jdbc -> jdbc.update(INSERT_ORDER, row.params())))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        }
    }

    @Test
    void givenAnOrderOfCustomerA_whenEachActorReadsOrders_thenOnlyItsOwnerAndTheStaffSeeIt() {
        var order = store(Row.guest().withUser(CUSTOMER_A_ID.value())
            .withTrackingHash(new TrackingToken("customer-a-order").hash()));

        assertThat(rls.as(CUSTOMER_A, this::orderIds)).containsExactly(order);
        assertThat(rls.as(CUSTOMER_B, this::orderIds)).isEmpty();
        assertThat(rls.as(GUEST, this::orderIds)).isEmpty();
        assertThat(rls.as(ADMIN, this::orderIds)).containsExactly(order);
        assertThat(rls.as(SYSTEM, this::orderIds)).containsExactly(order);
    }

    @Test
    void givenAPaidOrder_whenTheAdministratorShipsIt_thenOnlyTheStatusAndTrackingNumberMayChange() {
        var order = store(Row.guest().withStatus("PAID"));

        assertThat(rls.<Integer>as(ADMIN, jdbc -> jdbc.update("""
            update orders set status = 'SHIPPED', shipment_tracking_number = 'AR123456789', updated_at = now()
            where id = ?
            """, order))).isOne();
        for (String sql : List.of(
            "update orders set total_cents = 1, subtotal_cents = 1, shipping_cents = 0, discount_cents = 0 where id = ?",
            "update orders set customer_email = 'other@example.com' where id = ?")) {
            assertThatThrownBy(() -> rls.as(SYSTEM, jdbc -> jdbc.update(sql, order))).as(sql)
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("may only update the status");
        }
    }

    @Test
    void givenAnOrder_whenABuyerTriesToUpdateIt_thenNoRowChanges() {
        var order = store(Row.guest());

        assertThat(rls.<Integer>as(GUEST, jdbc -> jdbc.update("update orders set status = 'PAID' where id = ?", order)))
            .isZero();
    }

    private UUID store(Row row) {
        owner.update(INSERT_ORDER, row.params());
        return row.id();
    }

    private void rejects(UnaryOperator<Row> change, String constraint) {
        var row = change.apply(Row.guest());
        assertThatThrownBy(() -> owner.update(INSERT_ORDER, row.params())).as(constraint)
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    private List<UUID> orderIds(JdbcTemplate jdbc) {
        return jdbc.queryForList("select id from orders", UUID.class);
    }

    private List<UUID> itemOrderIds(JdbcTemplate jdbc) {
        return jdbc.queryForList("select order_id from order_item", UUID.class);
    }

    /** One order row with valid defaults: a guest's order of the test tracking token, awaiting payment. */
    private record Row(UUID id, UUID userId, String trackingHash, String status, String name, String email,
                       String phone, String nationalId, String floor, String province, String postalCode,
                       long subtotal, long discount, long shipping, long total, UUID couponId, String couponCode,
                       String shipmentTrackingNumber) {

        static Row guest() {
            return new Row(UUID.randomUUID(), null, GUEST_TRACKING.hash(), "AWAITING_PAYMENT", "Ana Pérez",
                "ana@example.com", "3425551234", "30123456", "2", "SANTA_FE", "S3000ABC", 2_000_000, 200_000,
                300_000, 2_100_000, null, null, null);
        }

        Object[] params() {
            return new Object[] {id, userId, trackingHash, status, name, email, phone, nationalId, "San Martín",
                "1234", floor, "B", "Santa Fe", province, postalCode, subtotal, discount, shipping, total, couponId,
                couponCode, shipmentTrackingNumber, null};
        }

        Row withId(UUID value) {
            return new Row(value, userId, trackingHash, status, name, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withUser(UUID value) {
            return new Row(id, value, trackingHash, status, name, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withTrackingHash(String value) {
            return new Row(id, userId, value, status, name, email, phone, nationalId, floor, province, postalCode,
                subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withStatus(String value) {
            return new Row(id, userId, trackingHash, value, name, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withName(String value) {
            return new Row(id, userId, trackingHash, status, value, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withEmail(String value) {
            return new Row(id, userId, trackingHash, status, name, value, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withPhone(String value) {
            return new Row(id, userId, trackingHash, status, name, email, value, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withNationalId(String value) {
            return new Row(id, userId, trackingHash, status, name, email, phone, value, floor, province, postalCode,
                subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withFloor(String value) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, value, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withProvince(String value) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, floor, value,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withPostalCode(String value) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, floor, province, value,
                subtotal, discount, shipping, total, couponId, couponCode, shipmentTrackingNumber);
        }

        Row withAmounts(long newSubtotal, long newDiscount, long newShipping, long newTotal) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, floor, province,
                postalCode, newSubtotal, newDiscount, newShipping, newTotal, couponId, couponCode,
                shipmentTrackingNumber);
        }

        Row withCoupon(UUID newCouponId, String newCouponCode) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, newCouponId, newCouponCode, shipmentTrackingNumber);
        }

        Row withShipmentTrackingNumber(String value) {
            return new Row(id, userId, trackingHash, status, name, email, phone, nationalId, floor, province,
                postalCode, subtotal, discount, shipping, total, couponId, couponCode, value);
        }
    }
}
