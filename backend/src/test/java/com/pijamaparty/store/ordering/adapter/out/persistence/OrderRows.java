package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test arrangement: complete order rows stored as the owner, until checkout creates them (T-E3-05). Only the states
 * without a shipment tracking number or delivery date.
 */
public final class OrderRows {

    // A random 64-character lowercase hex tracking token hash, unique per order.
    private static final String INSERT_ORDER = """
        insert into orders (id, number, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
                            payment_provider, terms_version, reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), md5(random()::text) || md5(random()::text), ?, 'Ana', 'Pérez',
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                2000000, 0, 300000, 2300000, 'MERCADOPAGO', '2026-10', ?, ?, ?)
        """;

    private OrderRows() {
    }

    /** An order in {@code status} whose 30-minute reservation expires at {@code reservationExpiresAt}. */
    public static void insert(JdbcTemplate owner, OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(status + " needs a tracking number");
        }
        var createdAt = reservationExpiresAt.minusSeconds(30 * 60).atOffset(ZoneOffset.UTC);
        owner.update(INSERT_ORDER, order.value(), status.name(), reservationExpiresAt.atOffset(ZoneOffset.UTC),
            createdAt, createdAt);
    }


    /** UC-15: an order that applied the coupon (code {@code VERANO10}); answers its number. */
    public static long insertWithCoupon(JdbcTemplate owner, OrderId order, OrderStatus status,
                                        Instant reservationExpiresAt, UUID couponId) {
        insert(owner, order, status, reservationExpiresAt);
        owner.update("update orders set coupon_id = ?, coupon_code = 'VERANO10' where id = ?", couponId,
            order.value());
        return numberOf(owner, order);
    }

    public static long numberOf(JdbcTemplate owner, OrderId order) {
        Long number = owner.queryForObject("select number from orders where id = ?", Long.class, order.value());
        if (number == null) {
            throw new IllegalStateException("no order " + order);
        }
        return number;
    }

    public static Optional<OrderStatus> statusOf(JdbcTemplate owner, OrderId order) {
        return owner.queryForList("select status from orders where id = ?", String.class, order.value()).stream()
            .findFirst().map(OrderStatus::valueOf);
    }
}
