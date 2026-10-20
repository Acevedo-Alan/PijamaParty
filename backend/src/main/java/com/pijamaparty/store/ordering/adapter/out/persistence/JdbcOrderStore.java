package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Buyer;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.application.AdminOrderViews;
import com.pijamaparty.store.ordering.application.BuyerEmails;
import com.pijamaparty.store.ordering.application.ConfirmableOrders;
import com.pijamaparty.store.ordering.application.CorrectableOrders;
import com.pijamaparty.store.ordering.application.CustomerOrderExports;
import com.pijamaparty.store.ordering.application.CustomerOrderPages;
import com.pijamaparty.store.ordering.application.CustomerOrderSummary;
import com.pijamaparty.store.ordering.application.CustomerOrderUnlinks;
import com.pijamaparty.store.ordering.application.CustomerOrderViews;
import com.pijamaparty.store.ordering.application.ExportedOrder;
import com.pijamaparty.store.ordering.application.GuestOrderLinks;
import com.pijamaparty.store.ordering.application.LatePayments;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.OrderPages;
import com.pijamaparty.store.ordering.application.OrderStatuses;
import com.pijamaparty.store.ordering.application.OrderSummary;
import com.pijamaparty.store.ordering.application.OrderTransitions;
import com.pijamaparty.store.ordering.application.OrdersToPay;
import com.pijamaparty.store.ordering.application.OverdueOrders;
import com.pijamaparty.store.ordering.application.PaidOrderDetails;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.application.SaveTrackingLinks;
import com.pijamaparty.store.ordering.application.ShipmentTrackingNumbers;
import com.pijamaparty.store.ordering.application.StatusChanges;
import com.pijamaparty.store.ordering.application.TrackedOrders;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.Savepoints;
import com.pijamaparty.store.shipping.api.Province;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders in tables {@code orders} and {@code order_item} (V14), in the caller's transaction ({@link #find} opens its
 * own when there is none). Row Level Security decides which orders the actor reaches (RNF-27).
 */
@Repository
class JdbcOrderStore
    implements OverdueOrders, OrderTransitions, SaveOrders, OrderNumbers, OrdersToPay, ConfirmableOrders,
    TrackedOrders, PaidOrderDetails, SaveTrackingLinks, OrderStatuses, OrderPages, AdminOrderViews, StatusChanges,
    ShipmentTrackingNumbers, LatePayments, CorrectableOrders, BuyerEmails, CustomerOrderPages, CustomerOrderViews,
    GuestOrderLinks, CustomerOrderExports, CustomerOrderUnlinks {

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
        INSERT INTO orders (id, number, user_id, tracking_token_hash, status, customer_first_name, customer_last_name,
                            customer_email, customer_phone, national_id, street, street_number, floor, apartment,
                            city, province, postal_code, subtotal_cents, discount_cents, shipping_cents,
                            total_cents, coupon_id, coupon_code, payment_provider, terms_version,
                            reservation_expires_at, created_at, updated_at)
        VALUES (:id, :number, :userId, :trackingTokenHash, 'AWAITING_PAYMENT', :customerFirstName, :customerLastName,
                :customerEmail, :customerPhone, :nationalId, :street, :streetNumber, :floor, :apartment, :city,
                :province, :postalCode, :subtotalCents, :discountCents, :shippingCents, :totalCents, :couponId,
                :couponCode, :paymentProvider, :termsVersion, :reservationExpiresAt, :createdAt, :createdAt)
        """;

    private static final String INSERT_ITEM = """
        INSERT INTO order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        VALUES (:id, :orderId, :position, :variantId, :sku, :productName, :size, :color, :unitPriceCents, :quantity)
        """;

    // UC-14: the buyer reads its own order (orders_tracking_select, orders_customer_select).
    private static final String SELECT_TO_PAY = """
        SELECT id, number, status, total_cents, payment_provider, customer_email,
               customer_first_name || ' ' || customer_last_name AS customer_full_name,
               reservation_expires_at
        FROM orders WHERE id = :id
        """;

    // UC-14 (T-E3-08): the order of a tracking link (orders_tracking_select): the checkout's token or one of a
    // confirmation email (T-E3-09). Filtering by the hash too keeps a customer, who also reads its other orders, to
    // this one.
    private static final String SELECT_TRACKED = """
        SELECT id, number, status, created_at, customer_first_name, city, province, subtotal_cents, discount_cents,
               shipping_cents, total_cents, coupon_code, reservation_expires_at, shipment_tracking_number
        FROM orders o
        WHERE o.tracking_token_hash = :hash
           OR EXISTS (SELECT 1 FROM order_tracking_token t WHERE t.order_id = o.id AND t.token_hash = :hash)
        """;

    private static final String SELECT_LINES = """
        SELECT variant_id, sku, product_name, size, color, unit_price_cents, quantity
        FROM order_item WHERE order_id = :orderId
        ORDER BY position
        """;

    // UC-15: the system locks the order an approved payment is for (orders_staff_select, orders_staff_update).
    private static final String LOCK_CONFIRMABLE = """
        SELECT id, number, status, coupon_id FROM orders
        WHERE id = :id
        FOR UPDATE
        """;

    // UC-17 (T-E3-09): the system reads the order a payment-confirmed email tells about (orders_staff_select).
    private static final String SELECT_PAID = """
        SELECT id, number, customer_first_name, customer_last_name, customer_email, street, street_number, floor,
               apartment, city, province, postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents,
               coupon_code
        FROM orders WHERE id = :id
        """;

    // UC-17 (T-E3-09): the system keeps the hash of an email's tracking token; T-E3-14: a buyer that repeats its
    // checkout keeps the hash of its new one, without reading the order (order_tracking_token_repeat_insert). The
    // foreign key tells a missing order; the hash is the primary key, and 256 random bits never repeat.
    private static final String INSERT_TRACKING_TOKEN = """
        INSERT INTO order_tracking_token (token_hash, order_id, created_at)
        VALUES (:hash, :id, :at)
        """;

    // UC-18 (T-E3-10): the administrator locks the order it changes (orders_staff_select, orders_staff_update).
    private static final String LOCK_STATUS = """
        SELECT status FROM orders WHERE id = :id FOR UPDATE
        """;

    // RNF-20.5: conditional, like TRANSITION; SHIPPED also saves the tracking number, DELIVERED the delivery instant.
    private static final String CHANGE = """
        UPDATE orders
        SET status = :to, updated_at = :at,
            shipment_tracking_number = COALESCE(:trackingNumber, shipment_tracking_number),
            delivered_at = CASE WHEN :delivered THEN :at ELSE delivered_at END
        WHERE id = :id AND status = :from
        """;

    private static final String CORRECT_TRACKING_NUMBER = """
        UPDATE orders SET shipment_tracking_number = :trackingNumber, updated_at = :at
        WHERE id = :id AND status = 'SHIPPED'
        """;

    // UC-17 (T-E3-23): the buyer locks the order of its tracking link (orders_tracking_select,
    // orders_buyer_email_update), filtered by the hash as SELECT_TRACKED.
    private static final String LOCK_CORRECTABLE = """
        SELECT id, number, status FROM orders o
        WHERE o.tracking_token_hash = :hash
           OR EXISTS (SELECT 1 FROM order_tracking_token t WHERE t.order_id = o.id AND t.token_hash = :hash)
        FOR UPDATE
        """;

    // UC-17 (T-E3-23): only the email (orders_buyer_email_update_guard).
    private static final String REPLACE_EMAIL = """
        UPDATE orders SET customer_email = :email, updated_at = :at
        WHERE id = :id
        """;

    // UC-22 (T-E4-08): over orders_guest_email_idx; orders_status_update_guard (V33) checks the customer again.
    private static final String LINK_GUEST_ORDERS = """
        UPDATE orders SET user_id = :customer, updated_at = :at
        WHERE user_id IS NULL AND unlinked_at IS NULL AND customer_email = :email
        """;

    // UC-15: set once, and only on a closed order (orders_paid_after_closing_ck).
    private static final String PAID_AFTER_CLOSING = """
        UPDATE orders SET paid_after_closing_at = :at, updated_at = :at
        WHERE id = :id AND paid_after_closing_at IS NULL AND status IN ('EXPIRED', 'CANCELLED')
        """;

    // UC-18: newest first, over orders_created_idx (V20); the number breaks ties between orders of the same instant.
    private static final String PAGE_ALL = """
        SELECT id, number, created_at, status, customer_first_name || ' ' || customer_last_name AS customer_full_name,
               city, province, total_cents
        FROM orders
        ORDER BY created_at DESC, number DESC
        LIMIT :limit OFFSET :offset
        """;

    // UC-18: by status, over orders_status_created_idx (V14).
    private static final String PAGE_BY_STATUS = """
        SELECT id, number, created_at, status, customer_first_name || ' ' || customer_last_name AS customer_full_name,
               city, province, total_cents
        FROM orders
        WHERE status = :status
        ORDER BY created_at DESC, number DESC
        LIMIT :limit OFFSET :offset
        """;

    private static final String SELECT_ADMIN_VIEW = """
        SELECT id, number, status, created_at, updated_at, delivered_at, customer_first_name, customer_last_name,
               customer_email, customer_phone, national_id, street, street_number, floor, apartment, city, province,
               postal_code, subtotal_cents, discount_cents, shipping_cents, total_cents, coupon_code, payment_provider,
               shipment_tracking_number, paid_after_closing_at
        FROM orders WHERE id = :id
        """;

    // UC-22 (T-E4-07): a customer's orders, newest first, over orders_user_created_idx (V14). orders_customer_select
    // already limits a customer to its own; the filter keeps out the order of a tracking token the session carries.
    private static final String PAGE_OF_CUSTOMER = """
        SELECT id, number, created_at, status, total_cents FROM orders
        WHERE user_id = :userId
        ORDER BY created_at DESC, number DESC
        LIMIT :limit OFFSET :offset
        """;

    private static final String SELECT_CUSTOMER_VIEW = """
        SELECT id, number, status, created_at, street, street_number, floor, apartment, city, province, postal_code,
               subtotal_cents, discount_cents, shipping_cents, total_cents, coupon_code, shipment_tracking_number
        FROM orders WHERE id = :id AND user_id = :userId
        """;

    // UC-28 (T-E4-16): every order of a customer for its data download, newest first, over orders_user_created_idx. As
    // PAGE_OF_CUSTOMER, orders_customer_select limits a customer to its own and the filter keeps out a tracked order.
    private static final String SELECT_CUSTOMER_EXPORT = """
        SELECT id, number, status, created_at, delivered_at, customer_first_name, customer_last_name, customer_email,
               customer_phone, national_id, street, street_number, floor, apartment, city, province, postal_code,
               subtotal_cents, discount_cents, shipping_cents, total_cents, coupon_code, payment_provider,
               shipment_tracking_number
        FROM orders
        WHERE user_id = :userId
        ORDER BY created_at DESC, number DESC
        """;

    // UC-28 (T-E4-16): the lines of every order of a customer in one query, for its data download.
    private static final String SELECT_CUSTOMER_EXPORT_LINES = """
        SELECT i.order_id, i.variant_id, i.sku, i.product_name, i.size, i.color, i.unit_price_cents, i.quantity
        FROM order_item i JOIN orders o ON o.id = i.order_id
        WHERE o.user_id = :userId
        ORDER BY i.order_id, i.position
        """;

    // UC-28 (T-E4-16): as the customer, once its user is gone. No policy lets a customer update its orders: the function
    // of V36 does it, and it and the buyer guard check both conditions.
    private static final String UNLINK_CUSTOMER_ORDERS = """
        SELECT unlink_deleted_customer_orders(:userId, :at)
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final IdGenerator ids;
    private final Savepoints savepoints;

    JdbcOrderStore(NamedParameterJdbcTemplate jdbc, IdGenerator ids, DataSource dataSource) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.savepoints = new Savepoints(dataSource);
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
            .addValue("customerFirstName", buyer.firstName())
            .addValue("customerLastName", buyer.lastName())
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

    /** Its own short transaction when the caller has none: paying an order runs outside one (RNF-21). */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderToPay> find(OrderId order) {
        return jdbc.query(SELECT_TO_PAY, new MapSqlParameterSource("id", order.value()), (row, n) -> new OrderToPay(
                OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
                OrderStatus.valueOf(row.getString("status")), Money.of(row.getLong("total_cents")),
                PaymentProvider.valueOf(row.getString("payment_provider")), row.getString("customer_full_name"),
                row.getString("customer_email"),
                row.getObject("reservation_expires_at", OffsetDateTime.class).toInstant()))
            .stream().findFirst();
    }

    /** Its own short transaction when the caller has none, so the order and its lines are read together. */
    @Override
    @Transactional(readOnly = true)
    public Optional<TrackedOrder> find(TrackingToken token) {
        return jdbc.query(SELECT_TRACKED, new MapSqlParameterSource("hash", token.hash()), (row, n) -> {
                OrderId id = OrderId.of(row.getObject("id", UUID.class));
                return new TrackedOrder(id, row.getLong("number"), OrderStatus.valueOf(row.getString("status")),
                    row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getString("customer_first_name"),
                    row.getString("city"), Province.valueOf(row.getString("province")), linesOf(id),
                    new OrderAmounts(Money.of(row.getLong("subtotal_cents")), Money.of(row.getLong("discount_cents")),
                        Money.of(row.getLong("shipping_cents")), Money.of(row.getLong("total_cents"))),
                    Optional.ofNullable(row.getString("coupon_code")),
                    row.getObject("reservation_expires_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getString("shipment_tracking_number")));
            })
            .stream().findFirst();
    }

    private List<OrderLine> linesOf(OrderId order) {
        return jdbc.query(SELECT_LINES, new MapSqlParameterSource("orderId", order.value()), (row, n) ->
            new OrderLine(row.getObject("variant_id", UUID.class), row.getString("sku"), row.getString("product_name"),
                row.getString("size"), row.getString("color"), Money.of(row.getLong("unit_price_cents")),
                row.getInt("quantity")));
    }

    @Override
    public Optional<ConfirmableOrder> lock(OrderId order) {
        return jdbc.query(LOCK_CONFIRMABLE, new MapSqlParameterSource("id", order.value()), (row, n) ->
                new ConfirmableOrder(OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
                    OrderStatus.valueOf(row.getString("status")),
                    Optional.ofNullable(row.getObject("coupon_id", UUID.class))))
            .stream().findFirst();
    }

    @Override
    public Optional<PaidOrder> detailsOf(OrderId order) {
        return jdbc.query(SELECT_PAID, new MapSqlParameterSource("id", order.value()), (row, n) -> {
                OrderId id = OrderId.of(row.getObject("id", UUID.class));
                List<Line> lines = linesOf(id).stream()
                    .map(line -> new Line(line.productName(), line.size(), line.color(), line.quantity(),
                        line.unitPrice(), line.amount()))
                    .toList();
                return new PaidOrder(id.value(), row.getLong("number"), row.getString("customer_first_name"),
                    row.getString("customer_last_name"), row.getString("customer_email"), lines,
                    Money.of(row.getLong("subtotal_cents")),
                    Money.of(row.getLong("discount_cents")), Optional.ofNullable(row.getString("coupon_code")),
                    Money.of(row.getLong("shipping_cents")), Money.of(row.getLong("total_cents")),
                    new Address(row.getString("street"), row.getString("street_number"),
                        Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
                        row.getString("city"), row.getString("province"),
                        row.getString("postal_code")));
            })
            .stream().findFirst();
    }

    @Override
    public boolean add(OrderId order, String tokenHash, Instant at) {
        var params = new MapSqlParameterSource("id", order.value())
            .addValue("hash", tokenHash)
            .addValue("at", at.atOffset(ZoneOffset.UTC));
        return savepoints.run(() -> jdbc.update(INSERT_TRACKING_TOKEN, params) == 1,
            violation -> Savepoints.isForeignKeyViolation(violation) ? Optional.of(false) : Optional.empty());
    }

    @Override
    public Optional<OrderStatus> lockStatus(OrderId order) {
        return jdbc.queryForList(LOCK_STATUS, new MapSqlParameterSource("id", order.value()), String.class).stream()
            .findFirst().map(OrderStatus::valueOf);
    }

    @Override
    public boolean change(OrderId order, StatusChange change, Instant at) {
        if (!change.from().canBecome(change.to())) {
            throw new IllegalArgumentException("an order cannot go from " + change.from() + " to " + change.to());
        }
        return jdbc.update(CHANGE, new MapSqlParameterSource("id", order.value())
            .addValue("from", change.from().name())
            .addValue("to", change.to().name())
            .addValue("trackingNumber", change.trackingNumber().map(ShipmentTrackingNumber::value).orElse(null),
                Types.VARCHAR)
            .addValue("delivered", change.to() == OrderStatus.DELIVERED)
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public boolean correct(OrderId order, ShipmentTrackingNumber number, Instant at) {
        return jdbc.update(CORRECT_TRACKING_NUMBER, new MapSqlParameterSource("id", order.value())
            .addValue("trackingNumber", number.value())
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public Optional<CorrectableOrder> lock(TrackingToken token) {
        return jdbc.query(LOCK_CORRECTABLE, new MapSqlParameterSource("hash", token.hash()), (row, n) ->
                new CorrectableOrder(OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
                    OrderStatus.valueOf(row.getString("status"))))
            .stream().findFirst();
    }

    @Override
    public boolean replace(OrderId order, String email, Instant at) {
        return jdbc.update(REPLACE_EMAIL, new MapSqlParameterSource("id", order.value())
            .addValue("email", email)
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public int link(UserId customer, String email, Instant at) {
        return jdbc.update(LINK_GUEST_ORDERS, new MapSqlParameterSource("customer", customer.value())
            .addValue("email", email)
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public void paidAfterClosing(OrderId order, Instant at) {
        jdbc.update(PAID_AFTER_CLOSING, new MapSqlParameterSource("id", order.value())
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public List<OrderSummary> page(Optional<OrderStatus> status, int offset, int limit) {
        var params = new MapSqlParameterSource("offset", offset).addValue("limit", limit);
        status.ifPresent(wanted -> params.addValue("status", wanted.name()));
        return status.isPresent()
            ? jdbc.query(PAGE_BY_STATUS, params, JdbcOrderStore::summary)
            : jdbc.query(PAGE_ALL, params, JdbcOrderStore::summary);
    }

    private static OrderSummary summary(ResultSet row, int rowNumber) throws SQLException {
        return new OrderSummary(
            OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(), OrderStatus.valueOf(row.getString("status")),
            row.getString("customer_full_name"), row.getString("city"), Province.valueOf(row.getString("province")),
            Money.of(row.getLong("total_cents")));
    }

    /** Its own short transaction when the caller has none, so the order and its lines are read together. */
    @Override
    @Transactional(readOnly = true)
    public Optional<AdminOrder> adminViewOf(OrderId order) {
        return jdbc.query(SELECT_ADMIN_VIEW, new MapSqlParameterSource("id", order.value()), (row, n) -> {
                OrderId id = OrderId.of(row.getObject("id", UUID.class));
                List<Line> lines = linesOf(id).stream()
                    .map(line -> new Line(line.productName(), line.size(), line.color(), line.quantity(),
                        line.unitPrice(), line.amount()))
                    .toList();
                return new AdminOrder(id.value(), row.getLong("number"), Status.valueOf(row.getString("status")),
                    List.of(), row.getObject("created_at", OffsetDateTime.class).toInstant(),
                    row.getObject("updated_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getObject("delivered_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant),
                    new Buyer(row.getString("customer_first_name"), row.getString("customer_last_name"),
                        row.getString("customer_email"), row.getString("customer_phone"),
                        row.getString("national_id")),
                    new Address(row.getString("street"), row.getString("street_number"),
                        Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
                        row.getString("city"), row.getString("province"), row.getString("postal_code")),
                    lines, Money.of(row.getLong("subtotal_cents")), Money.of(row.getLong("discount_cents")),
                    Optional.ofNullable(row.getString("coupon_code")), Money.of(row.getLong("shipping_cents")),
                    Money.of(row.getLong("total_cents")), row.getString("payment_provider"),
                    Optional.ofNullable(row.getString("shipment_tracking_number")),
                    row.getObject("paid_after_closing_at", OffsetDateTime.class) != null);
            })
            .stream().findFirst();
    }

    @Override
    public List<CustomerOrderSummary> page(UserId customer, int offset, int limit) {
        var params = new MapSqlParameterSource("userId", customer.value()).addValue("offset", offset)
            .addValue("limit", limit);
        return jdbc.query(PAGE_OF_CUSTOMER, params, (row, n) -> new CustomerOrderSummary(
            OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(), OrderStatus.valueOf(row.getString("status")),
            Money.of(row.getLong("total_cents"))));
    }

    /** Its own short transaction when the caller has none, so the order and its lines are read together. */
    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerOrder> find(UserId customer, OrderId order) {
        var params = new MapSqlParameterSource("id", order.value()).addValue("userId", customer.value());
        return jdbc.query(SELECT_CUSTOMER_VIEW, params, (row, n) -> {
                OrderId id = OrderId.of(row.getObject("id", UUID.class));
                return new CustomerOrder(id, row.getLong("number"), OrderStatus.valueOf(row.getString("status")),
                    row.getObject("created_at", OffsetDateTime.class).toInstant(), linesOf(id),
                    new OrderAmounts(Money.of(row.getLong("subtotal_cents")), Money.of(row.getLong("discount_cents")),
                        Money.of(row.getLong("shipping_cents")), Money.of(row.getLong("total_cents"))),
                    Optional.ofNullable(row.getString("coupon_code")),
                    new ShippingAddress(row.getString("street"), row.getString("street_number"),
                        Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
                        row.getString("city"), row.getString("postal_code")),
                    Province.valueOf(row.getString("province")),
                    Optional.ofNullable(row.getString("shipment_tracking_number")));
            })
            .stream().findFirst();
    }

    @Override
    public List<ExportedOrder> all(UserId customer) {
        var parameters = new MapSqlParameterSource("userId", customer.value());
        Map<UUID, List<OrderLine>> lines = new HashMap<>();
        jdbc.query(SELECT_CUSTOMER_EXPORT_LINES, parameters, row -> {
            lines.computeIfAbsent(row.getObject("order_id", UUID.class), order -> new ArrayList<>())
                .add(new OrderLine(row.getObject("variant_id", UUID.class), row.getString("sku"),
                    row.getString("product_name"), row.getString("size"), row.getString("color"),
                    Money.of(row.getLong("unit_price_cents")), row.getInt("quantity")));
        });
        return jdbc.query(SELECT_CUSTOMER_EXPORT, parameters, (row, n) -> {
            OrderId id = OrderId.of(row.getObject("id", UUID.class));
            return new ExportedOrder(id, row.getLong("number"), OrderStatus.valueOf(row.getString("status")),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(row.getObject("delivered_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant),
                new ExportedOrder.Buyer(row.getString("customer_first_name"), row.getString("customer_last_name"),
                    row.getString("customer_email"), row.getString("customer_phone"), row.getString("national_id")),
                new ShippingAddress(row.getString("street"), row.getString("street_number"),
                    Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
                    row.getString("city"), row.getString("postal_code")),
                Province.valueOf(row.getString("province")), lines.getOrDefault(id.value(), List.of()),
                new OrderAmounts(Money.of(row.getLong("subtotal_cents")), Money.of(row.getLong("discount_cents")),
                    Money.of(row.getLong("shipping_cents")), Money.of(row.getLong("total_cents"))),
                Optional.ofNullable(row.getString("coupon_code")), row.getString("payment_provider"),
                Optional.ofNullable(row.getString("shipment_tracking_number")));
        });
    }

    @Override
    public int unlinkAll(UserId customer, Instant at) {
        var parameters = new MapSqlParameterSource("userId", customer.value())
            .addValue("at", at.atOffset(ZoneOffset.UTC));
        Integer unlinked = jdbc.queryForObject(UNLINK_CUSTOMER_ORDERS, parameters, Integer.class);
        return unlinked == null ? 0 : unlinked;
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
