package com.pijamaparty.store.cart.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.cart.application.CartStoreContractTest;
import com.pijamaparty.store.cart.application.DeleteCart;
import com.pijamaparty.store.cart.application.DeleteIdleCarts;
import com.pijamaparty.store.cart.application.LoadCart;
import com.pijamaparty.store.cart.application.SaveCart;
import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the owner of the cart, and deletions as the system, so Row Level
 * Security applies as in production (RNF-27), then read back from PostgreSQL.
 */
@ApiTest
class JdbcCartStoreContractTest extends CartStoreContractTest<JdbcCartStoreContractTest.AsOwner> {

    private static final Map<CartOwner, Actor> ACTORS = Map.of(
        GUEST, guest(GUEST_TOKEN),
        OTHER_GUEST, guest(OTHER_GUEST_TOKEN),
        CUSTOMER, Actor.customer(((CartOwner.Customer) CUSTOMER).userId(), Optional.empty(), Optional.empty()));

    /** UC-19 (T-E4-04): the customer signing in from the browser of {@code GUEST}'s cart. */
    private static final Actor SIGNING_IN =
        Actor.customer(((CartOwner.Customer) CUSTOMER).userId(), Optional.of(GUEST_TOKEN), Optional.empty());

    private final AsOwner asOwner;
    private final JdbcTemplate owner;

    JdbcCartStoreContractTest(JdbcCartStore adapter, PlatformTransactionManager transactionManager,
                              PostgreSQLContainer<?> postgres) {
        this.asOwner = new AsOwner(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsOwner createSubject() {
        return asOwner;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart");
    }

    @Test
    void givenACartSavedByAnotherActor_whenSaved_thenRowLevelSecurityRejectsIt() {
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, Instant.parse("2026-10-10T12:00:00Z"));

        assertThatThrownBy(() -> asOwner.as(ACTORS.get(OTHER_GUEST), () -> {
            asOwner.adapter().save(cart);
            return null;
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isZero();
    }

    @Test
    void givenAnIdleCartWithItems_whenTheSystemDeletesIt_thenItsItemsGoWithIt() {
        Instant opened = Instant.parse("2026-10-10T12:00:00Z");
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, opened);
        cart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, Money.of(1_500_000), opened);
        asOwner.save(cart);

        assertThat(asOwner.deleteUpdatedBefore(opened.plusSeconds(1))).isEqualTo(1);

        assertThat(owner.queryForObject("select count(*) from cart_item", Long.class)).isZero();
    }

    @Test
    void givenAnIdleCart_whenAnotherGuestOrTheAdministratorDeletesIdleCarts_thenRowLevelSecurityKeepsIt() {
        Instant opened = Instant.parse("2026-10-10T12:00:00Z");
        Instant cutoff = opened.plusSeconds(1);
        asOwner.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, opened));

        assertThat(asOwner.as(ACTORS.get(OTHER_GUEST), () -> asOwner.adapter().deleteUpdatedBefore(cutoff))).isZero();
        assertThat(asOwner.as(RlsPolicyTester.ADMIN, () -> asOwner.adapter().deleteUpdatedBefore(cutoff))).isZero();
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isEqualTo(1);
    }

    @Test
    void givenAGuestCart_whenTheCustomerSigningInWithItsTokenReadsIt_thenOnlyThatCustomerFindsItWithItsItems() {
        Instant opened = Instant.parse("2026-10-10T12:00:00Z");
        Cart cart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, opened);
        cart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 2, 5, Money.of(1_500_000), opened);
        asOwner.save(cart);

        assertThat(asOwner.as(SIGNING_IN, () -> asOwner.adapter().findByOwner(GUEST)))
            .hasValueSatisfying(found -> assertThat(found.items()).hasSize(1));
        assertThat(asOwner.as(ACTORS.get(CUSTOMER), () -> asOwner.adapter().findByOwner(GUEST)))
            .as("a customer without the token").isEmpty();
        Actor otherToken = Actor.customer(((CartOwner.Customer) CUSTOMER).userId(), Optional.of(OTHER_GUEST_TOKEN),
            Optional.empty());
        assertThat(asOwner.as(otherToken, () -> asOwner.adapter().findByOwner(GUEST)))
            .as("a customer with another token").isEmpty();
    }

    @Test
    void givenTheCustomerSigningIn_whenDeletingAnotherGuestsCartOrChangingItsOwnTokensCart_thenRowLevelSecurityKeepsThem() {
        Instant opened = Instant.parse("2026-10-10T12:00:00Z");
        Cart guestCart = Cart.open(CartId.of(UUID.randomUUID()), GUEST, opened);
        Cart otherCart = Cart.open(CartId.of(UUID.randomUUID()), OTHER_GUEST, opened);
        asOwner.save(guestCart);
        asOwner.save(otherCart);

        assertThat(asOwner.delete(otherCart.id())).isFalse();
        assertThat(owner.queryForObject("select count(*) from cart", Long.class)).isEqualTo(2);

        guestCart.add(CartItemId.of(UUID.randomUUID()), VariantId.of(UUID.randomUUID()), 1, 5, Money.of(1_500_000),
            opened.plusSeconds(60));
        assertThatThrownBy(() -> asOwner.as(SIGNING_IN, () -> {
            asOwner.adapter().save(guestCart);
            return null;
        })).as("it only reads and deletes it").isInstanceOf(DataAccessException.class);
        assertThat(owner.queryForObject("select count(*) from cart_item", Long.class)).isZero();
    }

    private static Actor guest(CartToken token) {
        return Actor.anonymous(Optional.of(token), Optional.empty());
    }

    /** Runs every call in its own committed transaction as the actor that owns the cart. */
    record AsOwner(JdbcCartStore adapter, TransactionTemplate transactions)
        implements LoadCart, SaveCart, DeleteCart, DeleteIdleCarts {

        @Override
        public Optional<Cart> findByOwner(CartOwner owner) {
            return as(ACTORS.get(owner), () -> adapter.findByOwner(owner));
        }

        @Override
        public Optional<Cart> lockByOwner(CartOwner owner) {
            return as(ACTORS.get(owner), () -> adapter.lockByOwner(owner));
        }

        @Override
        public void save(Cart cart) {
            as(ACTORS.get(cart.owner()), () -> {
                adapter.save(cart);
                return null;
            });
        }

        @Override
        public boolean delete(CartId cartId) {
            return as(SIGNING_IN, () -> adapter.delete(cartId));
        }

        @Override
        public int deleteUpdatedBefore(Instant cutoff) {
            return as(Actor.system(), () -> adapter.deleteUpdatedBefore(cutoff));
        }

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }
    }
}
