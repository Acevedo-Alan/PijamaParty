package com.pijamaparty.store.ordering.adapter.out.cart;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CheckoutCartLock;
import com.pijamaparty.store.ordering.application.CheckoutCartsContractTest;
import com.pijamaparty.store.ordering.application.ClearCart;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the cart module to the cart, catalog, stock and coupon tables in PostgreSQL. Every call
 * is its own committed transaction as the actor who asks, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class CartModuleCheckoutCartsContractTest
    extends CheckoutCartsContractTest<CartModuleCheckoutCartsContractTest.AsActor> {

    private final AsActor asActor;
    private final JdbcTemplate owner;
    private final Map<Actor, UUID> cartOf = new HashMap<>();
    private int lineCount;

    CartModuleCheckoutCartsContractTest(CartModuleCheckoutCartLock lock, CartModuleClearCart clear,
                                        PlatformTransactionManager transactionManager,
                                        PostgreSQLContainer<?> postgres) {
        this.asActor = new AsActor(lock, clear, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsActor createSubject() {
        return asActor;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, "
            + "stock_level, product_image, variant, product, size_guide, category");
    }

    @Override
    protected UUID givenLine(Actor guest, long priceCents, long seenPriceCents, int quantity, int available) {
        UUID variant = sellable(priceCents, available);
        UUID cart = cartOf.computeIfAbsent(guest, actor -> {
            UUID id = UUID.randomUUID();
            owner.update("insert into cart (id, token_hash, created_at, updated_at) values (?, ?, now(), now())", id,
                actor.cartToken().orElseThrow().hash());
            return id;
        });
        // Lines show in the order they were added.
        owner.update("""
            insert into cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
            values (?, ?, ?, ?, ?, now() + make_interval(secs => ?))
            """, UUID.randomUUID(), cart, variant, quantity, seenPriceCents, ++lineCount);
        return variant;
    }

    @Override
    protected UUID givenCoupon(Actor guest, String code, int percent, boolean active) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, active, created_at, updated_at)
            values (?, ?, 'PERCENTAGE', ?, ?, now(), now())
            """, id, code, percent, active);
        owner.update("update cart set coupon_id = ? where id = ?", id, cartOf.get(guest));
        return id;
    }

    @Override
    protected UUID givenAutomaticCoupon(String code, int percent) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, automatic, active, created_at, updated_at)
            values (?, ?, 'PERCENTAGE', ?, true, true, now(), now())
            """, id, code, percent);
        return id;
    }

    /** A variant of a published product of an active category, with {@code available} units on hand. */
    private UUID sellable(long priceCents, int available) {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama', ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, "pijama-" + product);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, ?, true, now(), now())
            """, variantId, product, skuOf(variantId), priceCents);
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            available);
        return variantId;
    }

    /** Runs every call in its own committed transaction as the actor who asks. */
    record AsActor(CartModuleCheckoutCartLock lock, CartModuleClearCart clear, TransactionTemplate transactions)
        implements CheckoutCartLock, ClearCart {

        @Override
        public CartState lock(Actor actor) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> lock.lock(actor)));
        }

        @Override
        public void clear(Actor actor) {
            CurrentActor.runAs(actor, () -> transactions.executeWithoutResult(status -> clear.clear(actor)));
        }
    }
}
