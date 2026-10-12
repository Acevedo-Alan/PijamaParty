package com.pijamaparty.store.cart.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_CART;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E2-01: the cart schema (V12) enforces the UC-08 and UC-09 rules. Its Row Level Security policies are tested in
 * {@link CartRowLevelSecurityTest} (T-E2-13).
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

    private final JdbcTemplate owner;

    CartSchemaTest(PostgreSQLContainer<?> postgres) {
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart");
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
        owner.update(INSERT_CART, UUID.randomUUID(), GUEST_CART.hash(), null);

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
}
