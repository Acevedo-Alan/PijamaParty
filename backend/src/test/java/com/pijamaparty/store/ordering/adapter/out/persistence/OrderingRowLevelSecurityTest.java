package com.pijamaparty.store.ordering.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-21: the Row Level Security policies of {@code orders}, {@code order_item} (V14) and
 * {@code order_tracking_token} (V19), tested for reads, inserts, updates and deletes with customer A, customer B, a
 * guest, the administrator and the system, plus a missing context, a guest without a token, a guest holding the
 * token of a confirmation email and a customer holding a guest's token (RNF-27). A buyer reads its own orders and the
 * one of the tracking token it holds, and creates only its own order awaiting payment, bound to that token; items
 * follow their order; the administrator and the system read and update every order and nobody deletes one; only the
 * system mints tracking tokens. A buyer adding its token to a repeated checkout's order is
 * {@link RepeatedCheckoutTrackingRowLevelSecurityTest}. Every transaction is committed by the application role.
 */
@ApiTest
class OrderingRowLevelSecurityTest {

    private static final String INSERT_ORDER = """
        insert into orders (id, number, user_id, tracking_token_hash, status, customer_name, customer_email,
                            customer_phone, national_id, street, street_number, city, province, postal_code,
                            subtotal_cents, discount_cents, shipping_cents, total_cents, payment_provider,
                            terms_version, reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), ?, ?, ?, 'Ana Pérez', 'ana@example.com', '3425551234', '30123456',
                'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000', 2000000, 0, 300000, 2300000, 'MERCADOPAGO',
                '2026-10', now() + interval '30 minutes', now(), now())
        """;
    private static final String INSERT_ITEM = """
        insert into order_item (id, order_id, position, variant_id, sku, product_name, size, color, unit_price_cents,
                                quantity)
        values (?, ?, ?, ?, 'PP-ABCD2345', 'Pijama corto de algodón', 'M', 'Rosa', 2000000, 1)
        """;
    private static final String INSERT_TOKEN = """
        insert into order_tracking_token (token_hash, order_id, created_at) values (?, ?, now())
        """;
    private static final String RLS_VIOLATION = "row-level security";
    private static final String PERMISSION_DENIED = "permission denied";

    private static final TrackingToken CUSTOMER_A_TRACKING = new TrackingToken("customer-a-tracking-token");
    private static final TrackingToken CUSTOMER_B_TRACKING = new TrackingToken("customer-b-tracking-token");
    /** The token of a confirmation email (T-E3-09), stored in order_tracking_token for the EMAILED order. */
    private static final TrackingToken EMAILED_TRACKING = new TrackingToken("confirmation-email-tracking-token");

    /** The buyers of the seeded orders, each with the tracking token of its checkout. */
    enum Owner {
        CUSTOMER_A(CUSTOMER_A_ID.value(), CUSTOMER_A_TRACKING),
        CUSTOMER_B(CUSTOMER_B_ID.value(), CUSTOMER_B_TRACKING),
        GUEST(null, GUEST_TRACKING),
        OTHER_GUEST(null, new TrackingToken("another-guest-tracking-token")),
        // A guest's order whose confirmation email handed out EMAILED_TRACKING.
        EMAILED(null, new TrackingToken("emailed-order-checkout-tracking-token"));

        private final UUID userId;
        private final TrackingToken checkoutToken;

        Owner(UUID userId, TrackingToken checkoutToken) {
            this.userId = userId;
            this.checkoutToken = checkoutToken;
        }
    }

    private static final Set<Owner> ALL = EnumSet.allOf(Owner.class);
    private static final Set<Owner> NONE = EnumSet.noneOf(Owner.class);

    /**
     * Who runs the transaction ({@code actor} empty: no security context), the orders it reads, the orders it may
     * create (by their buyer and checkout token) and the tracking token rows it reads.
     */
    record Caller(String name, Optional<Actor> actor, Set<Owner> reads, Set<Owner> creates, Set<Owner> tokenRows) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        boolean isStaff() {
            return actor.filter(a -> a.is(Role.ADMIN) || a.is(Role.SYSTEM)).isPresent();
        }

        boolean isSystem() {
            return actor.filter(a -> a.is(Role.SYSTEM)).isPresent();
        }

        /** Buyers add lines to the orders they can see; the administrator and the system never do. */
        Set<Owner> addsItems() {
            return actor.filter(a -> a.is(Role.CUSTOMER) || a.is(Role.ANONYMOUS)).isPresent() ? reads : NONE;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.empty(),
                Optional.of(CUSTOMER_A_TRACKING))), EnumSet.of(Owner.CUSTOMER_A), EnumSet.of(Owner.CUSTOMER_A), NONE),
            new Caller("customer B", Optional.of(Actor.customer(CUSTOMER_B_ID, Optional.empty(),
                Optional.of(CUSTOMER_B_TRACKING))), EnumSet.of(Owner.CUSTOMER_B), EnumSet.of(Owner.CUSTOMER_B), NONE),
            new Caller("guest", Optional.of(GUEST), EnumSet.of(Owner.GUEST), EnumSet.of(Owner.GUEST), NONE),
            new Caller("administrator", Optional.of(ADMIN), ALL, NONE, NONE),
            new Caller("system", Optional.of(SYSTEM), ALL, NONE, ALL),
            new Caller("no context", Optional.empty(), NONE, NONE, NONE),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty())),
                NONE, NONE, NONE),
            new Caller("guest holding a confirmation email's token",
                Optional.of(Actor.anonymous(Optional.empty(), Optional.of(EMAILED_TRACKING))),
                EnumSet.of(Owner.EMAILED), NONE, EnumSet.of(Owner.EMAILED)),
            // Its token is a guest's checkout token, so it creates no order: not as a guest, nor bound to its own.
            new Caller("customer A holding the guest's token",
                Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(GUEST_TRACKING))),
                EnumSet.of(Owner.CUSTOMER_A, Owner.GUEST), NONE, NONE));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final Map<Owner, UUID> orders = new EnumMap<>(Owner.class);

    OrderingRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                 PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate order_tracking_token, order_item, orders");
    }

    @Test
    void givenTheOrderTables_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        for (String table : List.of("orders", "order_item", "order_tracking_token")) {
            var flags = owner.queryForMap(
                "select relrowsecurity, relforcerowsecurity from pg_class where oid = ?::regclass", table);
            assertThat(flags).as(table).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnOrderOfEveryBuyer_whenReading_thenOnlyTheReachableOrdersTheirItemsAndTheHeldTokensAreVisible(
            Caller caller) {
        seedOrders();

        var visibleOrders = caller.run(rls, sql -> sql.queryForList("select id from orders", UUID.class));
        var visibleItems = caller.run(rls, sql -> sql.queryForList("select order_id from order_item", UUID.class));
        var visibleTokens = caller.run(rls, sql -> sql.queryForList(
            "select order_id from order_tracking_token", UUID.class));

        assertThat(owners(visibleOrders)).as("orders").isEqualTo(caller.reads());
        assertThat(owners(visibleItems)).as("items").isEqualTo(caller.reads());
        assertThat(owners(visibleTokens)).as("tracking tokens").isEqualTo(caller.tokenRows());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoOrder_whenCreatingOneForEveryBuyer_thenOnlyItsOwnAwaitingPaymentIsAccepted(Caller caller) {
        for (Owner target : Owner.values()) {
            var create = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_ORDER, UUID.randomUUID(),
                target.userId, target.checkoutToken.hash(), "AWAITING_PAYMENT");
            if (caller.creates().contains(target)) {
                assertThat(caller.run(rls, create)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, create)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @Test
    void givenABuyer_whenItCreatesAnOrderNotAwaitingPaymentOrAsTheOtherKindOfBuyer_thenItIsRejected() {
        var customerA = Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(CUSTOMER_A_TRACKING));

        assertRejected(customerA, CUSTOMER_A_ID.value(), CUSTOMER_A_TRACKING, "PAID");
        assertRejected(GUEST, null, GUEST_TRACKING, "PAID");
        assertRejected(customerA, null, CUSTOMER_A_TRACKING, "AWAITING_PAYMENT");
        assertRejected(GUEST, CUSTOMER_A_ID.value(), GUEST_TRACKING, "AWAITING_PAYMENT");
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnOrderOfEveryBuyer_whenAddingALineToEach_thenOnlyTheVisibleOrdersOfABuyerAcceptIt(Caller caller) {
        seedOrders();

        for (Owner target : Owner.values()) {
            var add = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_ITEM, UUID.randomUUID(),
                orders.get(target), 1, UUID.randomUUID());
            if (caller.addsItems().contains(target)) {
                assertThat(caller.run(rls, add)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, add)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @Test
    void givenTheGuestsOrderAlreadyPaid_whenTheGuestAddsALineToIt_thenItIsRejected() {
        seedOrders();
        owner.update("update orders set status = 'PAID' where id = ?", orders.get(Owner.GUEST));

        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(INSERT_ITEM, UUID.randomUUID(),
                orders.get(Owner.GUEST), 1, UUID.randomUUID())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnOrderOfEveryBuyer_whenUpdatingAllOrders_thenOnlyTheAdministratorAndTheSystemChangeThem(
            Caller caller) {
        seedOrders();

        var updated = caller.run(rls, sql -> sql.queryForList(
            "update orders set status = 'CANCELLED', updated_at = now() returning id", UUID.class));

        var expected = caller.isStaff() ? ALL : NONE;
        assertThat(owners(updated)).isEqualTo(expected);
        assertThat(owners(owner.queryForList("select id from orders where status = 'CANCELLED'", UUID.class)))
            .as("stored").isEqualTo(expected);
    }

    @Test
    void givenAPaidOrder_whenTheAdministratorShipsIt_thenOnlyTheStatusAndTrackingNumberMayChange() {
        seedOrders();
        var order = orders.get(Owner.GUEST);
        owner.update("update orders set status = 'PAID' where id = ?", order);

        assertThat(rls.<Integer>as(ADMIN, sql -> sql.update("""
            update orders set status = 'SHIPPED', shipment_tracking_number = 'AR123456789', updated_at = now()
            where id = ?
            """, order))).isOne();
        for (String update : List.of(
            "update orders set total_cents = 1, subtotal_cents = 1, shipping_cents = 0, discount_cents = 0 where id = ?",
            "update orders set customer_email = 'other@example.com' where id = ?",
            "update orders set user_id = null, tracking_token_hash = md5('x') || md5('y') where id = ?")) {
            for (Actor staff : List.of(ADMIN, SYSTEM)) {
                assertThatThrownBy(() -> rls.as(staff, sql -> sql.update(update, order))).as(update)
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining("may only update the status");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnOrderOfEveryBuyer_whenMintingATrackingTokenForEach_thenOnlyTheSystemIsAccepted(Caller caller) {
        seedOrders();
        // A buyer tries the token it holds, as the repeated checkout does, but without an idempotency key of its own.
        var held = caller.actor().flatMap(Actor::trackingToken).map(TrackingToken::hash);

        for (Owner target : Owner.values()) {
            String tokenHash = held.orElseGet(() -> new TrackingToken(UUID.randomUUID().toString()).hash());
            var mint = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_TOKEN, tokenHash, orders.get(target));
            if (caller.isSystem()) {
                assertThat(caller.run(rls, mint)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, mint)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnOrderOfEveryBuyer_whenDeletingOrdersOrChangingItemsOrTokens_thenItIsDenied(Caller caller) {
        seedOrders();

        for (String sql : List.of("delete from orders", "update order_item set quantity = 2", "delete from order_item",
            "update order_tracking_token set created_at = now()", "delete from order_tracking_token")) {
            assertThatThrownBy(() -> caller.run(rls, jdbc -> jdbc.update(sql))).as(sql)
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(PERMISSION_DENIED);
        }
        assertThat(owner.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(Owner.values().length);
        assertThat(owner.queryForObject("select count(*) from order_item where quantity = 1", Integer.class))
            .isEqualTo(Owner.values().length);
    }

    private void assertRejected(Actor actor, UUID userId, TrackingToken token, String status) {
        assertThatThrownBy(() -> rls.as(actor, sql -> sql.update(INSERT_ORDER, UUID.randomUUID(), userId,
                token.hash(), status)))
            .as("%s, user %s, %s", actor.role(), userId, status)
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    /**
     * One order per buyer awaiting payment, each with one line and the tracking token of a confirmation email, stored
     * by the owner role (no policies). Only the EMAILED order's email token is held by a caller.
     */
    private void seedOrders() {
        for (Owner buyer : Owner.values()) {
            var order = UUID.randomUUID();
            owner.update(INSERT_ORDER, order, buyer.userId, buyer.checkoutToken.hash(), "AWAITING_PAYMENT");
            owner.update(INSERT_ITEM, UUID.randomUUID(), order, 0, UUID.randomUUID());
            var emailToken = buyer == Owner.EMAILED ? EMAILED_TRACKING : new TrackingToken(UUID.randomUUID().toString());
            owner.update(INSERT_TOKEN, emailToken.hash(), order);
            orders.put(buyer, order);
        }
    }

    /** The buyers of the given order ids; fails on a duplicate, so an order seen twice is not hidden by the set. */
    private Set<Owner> owners(List<UUID> orderIds) {
        assertThat(orderIds).doesNotHaveDuplicates();
        return orderIds.stream()
            .map(id -> orders.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst().orElseThrow()
                .getKey())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Owner.class)));
    }
}
