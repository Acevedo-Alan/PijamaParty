package com.pijamaparty.store.cart.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_CART;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
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
 * T-E2-13: the Row Level Security policies of {@code cart} and {@code cart_item} (V12, RNF-27), tested for reads,
 * inserts, updates and deletes with customer A, customer B, a guest, the administrator and the system, plus a missing
 * context, a guest without a token and a customer holding a guest's token. A customer reaches only its own cart, a
 * guest only the cart of its token, the system reads and deletes carts for the cleanup, and everybody else reaches
 * nothing; items follow their cart. Every transaction is committed by the application role.
 */
@ApiTest
class CartRowLevelSecurityTest {

    private static final String INSERT_CART = """
        insert into cart (id, token_hash, user_id, created_at, updated_at) values (?, ?, ?, now(), now())
        """;
    private static final String INSERT_ITEM = """
        insert into cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
        values (?, ?, ?, 1, 1500000, now())
        """;
    private static final String RLS_VIOLATION = "row-level security";

    /** The owners of the seeded carts: every caller is checked against each of them. */
    enum Owner {
        GUEST(GUEST_CART.hash(), null),
        OTHER_GUEST(new CartToken("another-guest-cart-token").hash(), null),
        CUSTOMER_A(null, CUSTOMER_A_ID.value()),
        CUSTOMER_B(null, CUSTOMER_B_ID.value());

        private final String tokenHash;
        private final UUID userId;

        Owner(String tokenHash, UUID userId) {
            this.tokenHash = tokenHash;
            this.userId = userId;
        }
    }

    /**
     * Who runs the transaction ({@code actor} empty: no security context), the carts it owns, those it only reads and
     * deletes when signing in (T-E4-04) and whether it is the cleanup, which reads and deletes every cart but never
     * touches items directly.
     */
    record Caller(String name, Optional<Actor> actor, Set<Owner> owns, Set<Owner> merges, boolean cleansUp) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        Set<Owner> reachableCarts() {
            return cleansUp ? EnumSet.allOf(Owner.class) : union(owns, merges);
        }

        Set<Owner> readableItems() {
            return union(owns, merges);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        Set<Owner> none = EnumSet.noneOf(Owner.class);
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A), EnumSet.of(Owner.CUSTOMER_A), none, false),
            new Caller("customer B", Optional.of(CUSTOMER_B), EnumSet.of(Owner.CUSTOMER_B), none, false),
            new Caller("guest", Optional.of(GUEST), EnumSet.of(Owner.GUEST), none, false),
            new Caller("administrator", Optional.of(ADMIN), none, none, false),
            new Caller("system", Optional.of(SYSTEM), none, none, true),
            new Caller("no context", Optional.empty(), none, none, false),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty())),
                none, none, false),
            // T-E4-04 (V32): signing in, it reads and deletes the guest cart of its token, but never changes it.
            new Caller("customer A holding the guest's token",
                Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.of(GUEST_CART), Optional.empty())),
                EnumSet.of(Owner.CUSTOMER_A), EnumSet.of(Owner.GUEST), false));
    }

    private static Set<Owner> union(Set<Owner> first, Set<Owner> second) {
        Set<Owner> union = EnumSet.noneOf(Owner.class);
        union.addAll(first);
        union.addAll(second);
        return union;
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final Map<Owner, UUID> carts = new EnumMap<>(Owner.class);

    CartRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                             PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart");
    }

    @Test
    void givenTheCartTables_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        for (String table : List.of("cart", "cart_item")) {
            var flags = owner.queryForMap(
                "select relrowsecurity, relforcerowsecurity from pg_class where oid = ?::regclass", table);
            assertThat(flags).as(table).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenACartOfEveryOwner_whenReading_thenOnlyTheReachableCartsAndTheOwnItemsAreVisible(Caller caller) {
        seedCarts();

        var visibleCarts = caller.run(rls, sql -> sql.queryForList("select id from cart", UUID.class));
        var visibleItems = caller.run(rls, sql -> sql.queryForList("select cart_id from cart_item", UUID.class));

        assertThat(owners(visibleCarts)).as("carts").isEqualTo(caller.reachableCarts());
        assertThat(owners(visibleItems)).as("items").isEqualTo(caller.readableItems());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoCart_whenCreatingOneForEveryOwner_thenOnlyItsOwnIsAccepted(Caller caller) {
        for (Owner target : Owner.values()) {
            var create = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_CART, UUID.randomUUID(),
                target.tokenHash, target.userId);
            if (caller.owns().contains(target)) {
                assertThat(caller.run(rls, create)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, create)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenACartOfEveryOwner_whenAddingAnItemToEach_thenOnlyItsOwnCartAcceptsIt(Caller caller) {
        seedCarts();

        for (Owner target : Owner.values()) {
            var add = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_ITEM, UUID.randomUUID(),
                carts.get(target), UUID.randomUUID());
            if (caller.owns().contains(target)) {
                assertThat(caller.run(rls, add)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, add)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenACartOfEveryOwner_whenUpdatingAllCartsAndItems_thenOnlyItsOwnChange(Caller caller) {
        seedCarts();

        var updatedCarts = caller.run(rls, sql -> sql.queryForList(
            "update cart set coupon_id = ?, updated_at = now() returning id", UUID.class, UUID.randomUUID()));
        var updatedItems = caller.run(rls, sql -> sql.queryForList(
            "update cart_item set quantity = 9 returning cart_id", UUID.class));

        assertThat(owners(updatedCarts)).as("carts").isEqualTo(caller.owns());
        assertThat(owners(updatedItems)).as("items").isEqualTo(caller.owns());
        assertThat(owners(owner.queryForList("select id from cart where coupon_id is not null", UUID.class)))
            .as("stored carts").isEqualTo(caller.owns());
        assertThat(owners(owner.queryForList("select cart_id from cart_item where quantity = 9", UUID.class)))
            .as("stored items").isEqualTo(caller.owns());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenACartOfEveryOwner_whenDeletingAllItems_thenOnlyTheOwnItemsAreDeleted(Caller caller) {
        seedCarts();

        var deleted = caller.run(rls, sql -> sql.queryForList("delete from cart_item returning cart_id", UUID.class));

        assertThat(owners(deleted)).isEqualTo(caller.owns());
        assertThat(owners(owner.queryForList("select cart_id from cart_item", UUID.class)))
            .isEqualTo(EnumSet.complementOf(withNone(caller.owns())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenACartOfEveryOwner_whenDeletingAllCarts_thenOnlyTheReachableCartsGoAndTheirItemsWithThem(Caller caller) {
        seedCarts();

        var deleted = caller.run(rls, sql -> sql.queryForList("delete from cart returning id", UUID.class));

        var remaining = EnumSet.complementOf(withNone(caller.reachableCarts()));
        assertThat(owners(deleted)).isEqualTo(caller.reachableCarts());
        assertThat(owners(owner.queryForList("select id from cart", UUID.class))).as("carts").isEqualTo(remaining);
        assertThat(owners(owner.queryForList("select cart_id from cart_item", UUID.class))).as("items")
            .isEqualTo(remaining);
    }

    @Test
    void givenACustomersCart_whenTheCustomerHandsItToAnotherCustomerOrAGuest_thenItIsRejected() {
        seedCarts();
        var cartOfA = carts.get(Owner.CUSTOMER_A);
        owner.update("delete from cart where id in (?, ?)", carts.get(Owner.CUSTOMER_B), carts.get(Owner.GUEST));

        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update("update cart set user_id = ? where id = ?",
                CUSTOMER_B_ID.value(), cartOfA)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update(
                "update cart set user_id = null, token_hash = ? where id = ?", GUEST_CART.hash(), cartOfA)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThat(owner.queryForObject("select user_id from cart where id = ?", UUID.class, cartOfA))
            .isEqualTo(CUSTOMER_A_ID.value());
    }

    @Test
    void givenAGuestsCart_whenTheGuestHandsItToAnotherTokenOrACustomer_thenItIsRejected() {
        seedCarts();
        var guestCart = carts.get(Owner.GUEST);
        owner.update("delete from cart where id in (?, ?)", carts.get(Owner.OTHER_GUEST), carts.get(Owner.CUSTOMER_A));

        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update("update cart set token_hash = ? where id = ?",
                Owner.OTHER_GUEST.tokenHash, guestCart)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(
                "update cart set token_hash = null, user_id = ? where id = ?", CUSTOMER_A_ID.value(), guestCart)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThat(owner.queryForObject("select token_hash from cart where id = ?", String.class, guestCart))
            .isEqualTo(GUEST_CART.hash());
    }

    @Test
    void givenAnItemInTheOwnCart_whenMovingItToAnotherActorsCart_thenItIsRejected() {
        seedCarts();

        assertThatThrownBy(() -> rls.as(CUSTOMER_A, sql -> sql.update("update cart_item set cart_id = ?",
                carts.get(Owner.CUSTOMER_B))))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update("update cart_item set cart_id = ?",
                carts.get(Owner.CUSTOMER_A))))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        assertThat(owner.queryForObject("select count(distinct cart_id) from cart_item", Integer.class))
            .isEqualTo(Owner.values().length);
    }

    /** One cart per owner, each with one item, stored by the owner role (no policies). */
    private void seedCarts() {
        for (Owner cartOwner : Owner.values()) {
            var cart = UUID.randomUUID();
            owner.update(INSERT_CART, cart, cartOwner.tokenHash, cartOwner.userId);
            owner.update(INSERT_ITEM, UUID.randomUUID(), cart, UUID.randomUUID());
            carts.put(cartOwner, cart);
        }
    }

    /** The owners of the given cart ids; fails on a duplicate, so a cart seen twice is not hidden by the set. */
    private Set<Owner> owners(List<UUID> cartIds) {
        assertThat(cartIds).doesNotHaveDuplicates();
        return cartIds.stream()
            .map(id -> carts.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst().orElseThrow().getKey())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Owner.class)));
    }

    /** EnumSet.copyOf refuses an empty plain collection; this keeps the element type for it. */
    private static EnumSet<Owner> withNone(Set<Owner> owners) {
        return owners.isEmpty() ? EnumSet.noneOf(Owner.class) : EnumSet.copyOf(owners);
    }
}
