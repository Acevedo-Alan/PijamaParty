package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.NewOrdersContractTest;
import com.pijamaparty.store.ordering.application.OrderNumbers;
import com.pijamaparty.store.ordering.application.OrdersToPay;
import com.pijamaparty.store.ordering.application.SaveOrders;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.shipping.api.Province;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the buyer that holds the order's tracking token, as the checkout
 * runs, so Row Level Security applies as in production (RNF-27). Read back as the owner.
 */
@ApiTest
class JdbcNewOrdersContractTest extends NewOrdersContractTest<JdbcNewOrdersContractTest.AsBuyer> {

    private final AsBuyer asBuyer;
    private final JdbcTemplate owner;

    JdbcNewOrdersContractTest(JdbcOrderStore adapter, PlatformTransactionManager transactionManager,
                              PostgreSQLContainer<?> postgres) {
        this.asBuyer = new AsBuyer(adapter, new TransactionTemplate(transactionManager), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsBuyer createSubject() {
        return asBuyer;
    }

    @Override
    protected void tokenIssued(TrackingToken token) {
        asBuyer.tokens().put(token.hash(), token);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate withdrawal_request, order_tracking_token, order_item, orders");
    }

    @Test
    void givenABuyerWithAnotherTrackingToken_whenInsertingTheOrder_thenRowLevelSecurityRefusesIt() {
        SaveOrders.NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        var stranger = Actor.anonymous(Optional.empty(), Optional.of(new TrackingToken("another-tracking-token")));

        assertThatThrownBy(() -> asBuyer.insertAs(stranger, order)).isInstanceOf(RuntimeException.class);
        assertThat(storedOrder(order.id())).isEmpty();
    }

    @Test
    void givenAGuest_whenInsertingTheOrderOfACustomer_thenRowLevelSecurityRefusesIt() {
        SaveOrders.NewOrder order = newOrder(Optional.of(UserId.of(UUID.randomUUID())), Optional.empty(),
            Optional.empty(), Optional.empty());
        var guest = Actor.anonymous(Optional.empty(), Optional.of(asBuyer.tokens().get(order.trackingTokenHash())));

        assertThatThrownBy(() -> asBuyer.insertAs(guest, order)).isInstanceOf(RuntimeException.class);
        assertThat(storedOrder(order.id())).isEmpty();
    }

    @Test
    void givenAPlacedOrder_whenAStrangerLooksForItToPay_thenRowLevelSecurityHidesIt() {
        SaveOrders.NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(order);
        var stranger = Actor.anonymous(Optional.empty(), Optional.of(new TrackingToken("another-tracking-token")));

        assertThat(CurrentActor.callAs(stranger, () -> asBuyer.adapter().find(order.id()))).isEmpty();
    }

    @Override
    protected Optional<SaveOrders.NewOrder> storedOrder(OrderId order) {
        List<OrderLine> lines = owner.query("""
            select variant_id, sku, product_name, size, color, unit_price_cents, quantity
            from order_item where order_id = ? order by position
            """, (row, n) -> new OrderLine(row.getObject("variant_id", UUID.class), row.getString("sku"),
            row.getString("product_name"), row.getString("size"), row.getString("color"),
            Money.of(row.getLong("unit_price_cents")), row.getInt("quantity")), order.value());
        return owner.query("select * from orders where id = ?", (row, n) -> toOrder(row, lines), order.value())
            .stream().findFirst();
    }

    @Override
    protected Optional<OrderStatus> statusOf(OrderId order) {
        return OrderRows.statusOf(owner, order);
    }

    private static SaveOrders.NewOrder toOrder(ResultSet row, List<OrderLine> lines) throws SQLException {
        UUID couponId = row.getObject("coupon_id", UUID.class);
        String couponCode = row.getString("coupon_code");
        UUID userId = row.getObject("user_id", UUID.class);
        return new SaveOrders.NewOrder(OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
            row.getString("tracking_token_hash"), Optional.ofNullable(userId).map(UserId::of),
            new BuyerDetails(row.getString("customer_name"), row.getString("customer_email"),
                row.getString("customer_phone"), row.getString("national_id")),
            new ShippingAddress(row.getString("street"), row.getString("street_number"),
                Optional.ofNullable(row.getString("floor")), Optional.ofNullable(row.getString("apartment")),
                row.getString("city"), row.getString("postal_code")),
            Province.valueOf(row.getString("province")), lines,
            new OrderAmounts(Money.of(row.getLong("subtotal_cents")), Money.of(row.getLong("discount_cents")),
                Money.of(row.getLong("shipping_cents")), Money.of(row.getLong("total_cents"))),
            Optional.ofNullable(couponId).map(id -> new SaveOrders.AppliedCoupon(id, couponCode)),
            PaymentProvider.valueOf(row.getString("payment_provider")), row.getString("terms_version"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(),
            row.getObject("reservation_expires_at", OffsetDateTime.class).toInstant());
    }

    /** Each call in its own committed transaction, as the buyer of the order's tracking token. */
    record AsBuyer(JdbcOrderStore adapter, TransactionTemplate transactions, Map<String, TrackingToken> tokens,
                   Map<OrderId, Actor> buyers) implements SaveOrders, OrderNumbers, OrdersToPay {

        /**
         * As the buyer who placed it, as the payment is opened after the checkout (T-E3-06), in the adapter's own
         * transaction; an unknown order as a guest without a token.
         */
        @Override
        public Optional<OrderToPay> find(OrderId order) {
            Actor buyer = buyers.getOrDefault(order, Actor.anonymous(Optional.empty(), Optional.empty()));
            return CurrentActor.callAs(buyer, () -> adapter.find(order));
        }

        @Override
        public void insert(NewOrder order) {
            TrackingToken token = tokens.get(order.trackingTokenHash());
            Actor buyer = order.userId()
                .map(user -> Actor.customer(user, Optional.empty(), Optional.of(token)))
                .orElseGet(() -> Actor.anonymous(Optional.empty(), Optional.of(token)));
            insertAs(buyer, order);
            buyers.put(order.id(), buyer);
        }

        void insertAs(Actor buyer, NewOrder order) {
            CurrentActor.runAs(buyer, () -> transactions.executeWithoutResult(status -> adapter.insert(order)));
        }

        @Override
        public long next() {
            Long number = CurrentActor.callAs(Actor.anonymous(Optional.empty(), Optional.empty()),
                () -> transactions.execute(status -> adapter.next()));
            return number == null ? 0 : number;
        }
    }
}
