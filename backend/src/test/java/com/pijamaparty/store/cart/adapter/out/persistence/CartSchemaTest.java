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
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
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
 * T-E2-01: the cart schema (V12) enforces the UC-08 and UC-09 rules, and its Row Level Security keeps each cart to
 * its owner (RNF-27), as committed transactions of the application role. The full policy matrix is T-E2-13.
 */
@ApiTest
class CartSchemaTest {

    private static final String INSERT_CART = """
        insert into cart (id, token_hash, user_id, created_at, updated_at) values (?, ?, ?, now(), now())
        """;
    private static final String INSERT_ITEM = """
        insert into cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
        values (?, ?, ?, ?, 1500000, now())
        """;
    private static final String OTHER_GUEST_HASH = new CartToken("another-guest-cart-token").hash();

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    CartSchemaTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
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

    @Test
    void givenAVisitorWithoutACart_whenAddingTheFirstProduct_thenACartTiedToItsTokenIsCreated() {
        var cart = UUID.randomUUID();

        rls.as(GUEST, sql -> {
            sql.update(INSERT_CART, cart, GUEST_CART.hash(), null);
            return sql.update(INSERT_ITEM, UUID.randomUUID(), cart, UUID.randomUUID(), 2);
        });

        assertThat(rls.as(GUEST, this::cartIds)).containsExactly(cart);
        assertThat(rls.as(GUEST, this::itemCartIds)).containsExactly(cart);
    }

    @Test
    void givenCartsOfTwoCustomersAndTwoGuests_whenEachReads_thenEachSeesOnlyItsOwnCartAndItems() {
        var guestCart = seedCart(GUEST_CART.hash(), null);
        var otherGuestCart = seedCart(OTHER_GUEST_HASH, null);
        var cartOfA = seedCart(null, CUSTOMER_A_ID.value());
        var cartOfB = seedCart(null, CUSTOMER_B_ID.value());

        assertThat(rls.as(GUEST, this::cartIds)).containsExactly(guestCart);
        assertThat(rls.as(GUEST, this::itemCartIds)).containsExactly(guestCart);
        assertThat(rls.as(CUSTOMER_A, this::cartIds)).containsExactly(cartOfA);
        assertThat(rls.as(CUSTOMER_A, this::itemCartIds)).containsExactly(cartOfA);
        assertThat(rls.as(CUSTOMER_B, this::cartIds)).containsExactly(cartOfB);
        assertThat(rls.as(CUSTOMER_B, this::itemCartIds)).containsExactly(cartOfB);
        assertThat(otherGuestCart).isNotIn(rls.as(GUEST, this::cartIds));
    }

    @Test
    void givenNoSecurityContextOrTheAdministrator_whenReadingCarts_thenNoRowIsVisible() {
        seedCart(GUEST_CART.hash(), null);
        seedCart(null, CUSTOMER_A_ID.value());

        assertThat(rls.withoutContext(this::cartIds)).isEmpty();
        assertThat(rls.withoutContext(this::itemCartIds)).isEmpty();
        assertThat(rls.as(ADMIN, this::cartIds)).isEmpty();
        assertThat(rls.as(ADMIN, this::itemCartIds)).isEmpty();
    }

    @Test
    void givenAGuest_whenCreatingACartForAnotherTokenOrACustomer_thenItIsRejected() {
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(INSERT_CART, UUID.randomUUID(), OTHER_GUEST_HASH,
                null)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(INSERT_CART, UUID.randomUUID(), null,
                CUSTOMER_A_ID.value())))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenAnotherActorsCart_whenAddingAnItemToIt_thenItIsRejected() {
        var cartOfA = seedCart(null, CUSTOMER_A_ID.value());

        assertThatThrownBy(() -> rls.as(GUEST, sql -> sql.update(INSERT_ITEM, UUID.randomUUID(), cartOfA,
                UUID.randomUUID(), 1)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThatThrownBy(() -> rls.as(CUSTOMER_B, sql -> sql.update(INSERT_ITEM, UUID.randomUUID(), cartOfA,
                UUID.randomUUID(), 1)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
    }

    @Test
    void givenAnIdleCart_whenTheSystemCleansUp_thenTheCartAndItsItemsAreDeleted() {
        var cart = seedCart(GUEST_CART.hash(), null);
        owner.update("update cart set updated_at = now() - interval '31 days' where id = ?", cart);

        int deleted = rls.as(SYSTEM, sql -> sql.update(
            "delete from cart where updated_at < now() - interval '30 days'"));

        assertThat(deleted).isOne();
        assertThat(owner.queryForObject("select count(*) from cart_item", Integer.class)).isZero();
    }

    @Test
    void givenTheSystem_whenChangingACartOrItsItems_thenNothingChanges() {
        var cart = seedCart(GUEST_CART.hash(), null);

        int updated = rls.as(SYSTEM, sql -> sql.update("update cart set coupon_id = ? where id = ?", UUID.randomUUID(),
            cart));
        int items = rls.as(SYSTEM, sql -> sql.update("update cart_item set quantity = 9"));

        assertThat(updated).isZero();
        assertThat(items).isZero();
        assertThat(owner.queryForObject("select quantity from cart_item", Integer.class)).isEqualTo(1);
    }

    @Test
    void givenACartWithoutOwnerOrWithTwo_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> owner.update(INSERT_CART, UUID.randomUUID(), null, null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cart_owner_ck");
        assertThatThrownBy(() -> owner.update(INSERT_CART, UUID.randomUUID(), GUEST_CART.hash(), CUSTOMER_A_ID.value()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cart_owner_ck");
    }

    @Test
    void givenATokenInClearText_whenStoredAsTheHash_thenItIsRejected() {
        assertThatThrownBy(() -> owner.update(INSERT_CART, UUID.randomUUID(), GUEST_CART.value(), null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cart_token_hash_format_ck");
    }

    @Test
    void givenACartForAToken_whenAnotherCartTakesTheSameToken_thenItIsRejected() {
        seedCart(GUEST_CART.hash(), null);

        assertThatThrownBy(() -> owner.update(INSERT_CART, UUID.randomUUID(), GUEST_CART.hash(), null))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("cart_token_hash_uk");
    }

    @Test
    void givenAVariantInTheCart_whenASecondLineOfItIsStored_thenItIsRejected() {
        var cart = UUID.randomUUID();
        var variant = UUID.randomUUID();
        owner.update(INSERT_CART, cart, GUEST_CART.hash(), null);
        owner.update(INSERT_ITEM, UUID.randomUUID(), cart, variant, 1);

        assertThatThrownBy(() -> owner.update(INSERT_ITEM, UUID.randomUUID(), cart, variant, 1))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("cart_item_cart_variant_uk");
    }

    @Test
    void givenAQuantityOf0_whenStored_thenItIsRejected() {
        var cart = UUID.randomUUID();
        owner.update(INSERT_CART, cart, GUEST_CART.hash(), null);

        assertThatThrownBy(() -> owner.update(INSERT_ITEM, UUID.randomUUID(), cart, UUID.randomUUID(), 0))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cart_item_quantity_ck");
    }

    /** A cart with one item of quantity 1, stored by the owner role (no policies). */
    private UUID seedCart(String tokenHash, UUID userId) {
        var cart = UUID.randomUUID();
        owner.update(INSERT_CART, cart, tokenHash, userId);
        owner.update(INSERT_ITEM, UUID.randomUUID(), cart, UUID.randomUUID(), 1);
        return cart;
    }

    private List<UUID> cartIds(JdbcTemplate sql) {
        return sql.queryForList("select id from cart", UUID.class);
    }

    private List<UUID> itemCartIds(JdbcTemplate sql) {
        return sql.queryForList("select cart_id from cart_item", UUID.class);
    }
}
