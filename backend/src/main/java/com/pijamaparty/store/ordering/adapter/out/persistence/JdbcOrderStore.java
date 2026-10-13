package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.OrderTransitions;
import com.pijamaparty.store.ordering.application.OverdueOrders;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Orders in tables {@code orders} and {@code order_item} (V14), in the caller's transaction. Row Level Security
 * decides which orders the actor reaches (RNF-27).
 */
@Repository
class JdbcOrderStore implements OverdueOrders, OrderTransitions, SaveOrders, OrderNumbers {

    // UC-16: oldest expiry first, over the partial index orders_reservation_expiry_idx. SKIP LOCKED lets two runs work
    // side by side without expiring the same order.
    private static final String LOCK_NEXT_DUE = """
        SELECT id FROM orders
        WHERE status = 'AWAITING_PAYMENT' AND reservation_expires_at <= :now
        ORDER BY reservation_expires_at, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """;

    // RNF-20.5: conditional, so a transition that lost the race affects no row.
    private static final String TRANSITION = """
        UPDATE orders SET status = :to, updated_at = :at
        WHERE id = :id AND status = :from
        """;

    // RNF-20.6: a sequence never hands out the same number twice, even to transactions that roll back.
    private static final String NEXT_NUMBER = "SELECT nextval('order_number_seq')";

    // UC-14: the buyer creates its own order, awaiting payment and bound to its tracking token (orders_*_insert).
    private static final String INSERT_ORDER = """
        INSERT INTO orders (id, number, user_id, tracking_token_hash, status, customer_name, customer_email,
                            customer_phone, national_id, street, street_number, floor, apartment, city, province,
                            postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents, coupon_id,
                            coupon_code, payment_provider, terms_version, reservation_expires_at, created_at,
                            updated_at)
        VALUES (:id, :number, :userId, :trackingTokenHash, 'AWAITING_PAYMENT', :customerName, :customerEmail,
                :customerPhone, :nationalId, :street, :streetNumber, :floor, :apartment, :city, :province,
                :postalCode, :subtotalCents, :discountCents, :shippingCents, :totalCents, :couponId, :couponCode,
                :paymentProvider, :termsVersion, :reservationExpiresAt, :createdAt, :createdAt)
        """;

    private static final String INSERT_ITEM = """
        INSERT INTO order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        VALUES (:id, :orderId, :position, :variantId, :sku, :productName, :size, :color, :unitPriceCents, :quantity)
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final IdGenerator ids;

    JdbcOrderStore(NamedParameterJdbcTemplate jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<OrderId> lockNextDue(Instant now) {
        return jdbc.queryForList(LOCK_NEXT_DUE, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)),
            UUID.class).stream().findFirst().map(OrderId::of);
    }

    @Override
    public boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at) {
        if (!from.canBecome(to)) {
            throw new IllegalArgumentException("an order cannot go from " + from + " to " + to);
        }
        return jdbc.update(TRANSITION, new MapSqlParameterSource("id", order.value())
            .addValue("from", from.name())
            .addValue("to", to.name())
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public long next() {
        Long number = jdbc.queryForObject(NEXT_NUMBER, EmptySqlParameterSource.INSTANCE, Long.class);
        if (number == null) {
            throw new IllegalStateException("order_number_seq returned no number");
        }
        return number;
    }

    @Override
    public void insert(NewOrder order) {
        var buyer = order.buyer();
        var address = order.address();
        var amounts = order.amounts();
        jdbc.update(INSERT_ORDER, new MapSqlParameterSource("id", order.id().value())
            .addValue("number", order.number())
            .addValue("userId", order.userId().map(UserId::value).orElse(null), Types.OTHER)
            .addValue("trackingTokenHash", order.trackingTokenHash())
            .addValue("customerName", buyer.name())
            .addValue("customerEmail", buyer.email())
            .addValue("customerPhone", buyer.phone())
            .addValue("nationalId", buyer.nationalId())
            .addValue("street", address.street())
            .addValue("streetNumber", address.number())
            .addValue("floor", address.floor().orElse(null), Types.VARCHAR)
            .addValue("apartment", address.apartment().orElse(null), Types.VARCHAR)
            .addValue("city", address.city())
            .addValue("province", order.province().name())
            .addValue("postalCode", address.postalCode())
            .addValue("subtotalCents", amounts.subtotal().cents())
            .addValue("discountCents", amounts.discount().cents())
            .addValue("shippingCents", amounts.shipping().cents())
            .addValue("totalCents", amounts.total().cents())
            .addValue("couponId", order.coupon().map(AppliedCoupon::couponId).orElse(null), Types.OTHER)
            .addValue("couponCode", order.coupon().map(AppliedCoupon::code).orElse(null), Types.VARCHAR)
            .addValue("paymentProvider", order.paymentProvider().name())
            .addValue("termsVersion", order.termsVersion())
            .addValue("reservationExpiresAt", order.reservationExpiresAt().atOffset(ZoneOffset.UTC))
            .addValue("createdAt", order.createdAt().atOffset(ZoneOffset.UTC)));
        List<OrderLine> lines = order.lines();
        SqlParameterSource[] items = IntStream.range(0, lines.size())
            .mapToObj(position -> item(order.id(), position, lines.get(position)))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_ITEM, items);
    }

    private SqlParameterSource item(OrderId order, int position, OrderLine line) {
        return new MapSqlParameterSource("id", ids.newId())
            .addValue("orderId", order.value())
            .addValue("position", position)
            .addValue("variantId", line.variantId())
            .addValue("sku", line.sku())
            .addValue("productName", line.productName())
            .addValue("size", line.size())
            .addValue("color", line.color())
            .addValue("unitPriceCents", line.unitPrice().cents())
            .addValue("quantity", line.quantity());
    }
}
