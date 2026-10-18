package com.pijamaparty.store.ordering.adapter.out.cart;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.CartAmounts;
import com.pijamaparty.store.ordering.application.CartAmountsContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Types;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the cart module to the cart, catalog, stock and coupon tables in PostgreSQL. Every call
 * is its own committed transaction as the actor who asks, so Row Level Security applies as in production (RNF-27).
 */
@ApiTest
class CartModuleAmountsContractTest extends CartAmountsContractTest<CartModuleAmountsContractTest.AsActor> {

    private final AsActor asActor;
    private final JdbcTemplate owner;

    CartModuleAmountsContractTest(CartModuleAmounts adapter, PlatformTransactionManager transactionManager,
                                  PostgreSQLContainer<?> postgres) {
        this.asActor = new AsActor(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsActor createSubject() {
        return asActor;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart, coupon_scope, coupon_redemption, coupon, stock_movement, stock_level, "
            + "product_image, variant, product, size_guide, category");
    }

    @Override
    protected void givenGuestCart(Actor guest, long unitPriceCents, int quantity, int available,
                                  OptionalInt couponPercent) {
        UUID variant = sellable(unitPriceCents, available);
        UUID coupon = couponPercent.isPresent() ? coupon(couponPercent.getAsInt()) : null;
        UUID cart = UUID.randomUUID();
        owner.update("""
            insert into cart (id, token_hash, coupon_id, created_at, updated_at) values (?, ?, ?, now(), now())
            """, cart, guest.cartToken().orElseThrow().hash(), new SqlParameterValue(Types.OTHER, coupon));
        owner.update("""
            insert into cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
            values (?, ?, ?, ?, ?, now())
            """, UUID.randomUUID(), cart, variant, quantity, unitPriceCents);
    }

    private UUID coupon(int percent) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into coupon (id, code, type, discount_value, minimum_purchase_cents, active, created_at, updated_at)
            values (?, ?, 'PERCENTAGE', ?, 1, true, now(), now())
            """, id, "CUPON" + id.toString().substring(0, 8).toUpperCase(), percent);
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
            """, variantId, product, "PP-" + variantId.toString().substring(0, 8), priceCents);
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variantId,
            available);
        return variantId;
    }

    /** Runs every call in its own committed transaction as the actor who asks. */
    record AsActor(CartModuleAmounts adapter, TransactionTemplate transactions) implements CartAmounts {

        @Override
        public Optional<Amounts> of(Actor actor) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> adapter.of(actor)));
        }
    }
}
