package com.pijamaparty.store.cart.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.cart.application.VariantAvailabilityContractTest;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's and the inventory's public APIs to their tables in PostgreSQL. */
@ApiTest
class CatalogVariantAvailabilityContractTest extends VariantAvailabilityContractTest<CatalogVariantAvailability> {

    private final CatalogVariantAvailability adapter;
    private final JdbcTemplate owner;

    CatalogVariantAvailabilityContractTest(CatalogVariantAvailability adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogVariantAvailability createSubject() {
        return adapter;
    }

    @Override
    protected void givenSold(VariantId variantId, UUID productId, UUID categoryId, Money price, Integer available) {
        variantOf(product(productId, categoryId, true), variantId, price);
        if (available != null) {
            stock(variantId, available);
        }
    }

    @Override
    protected void givenNotSold(VariantId variantId, int available) {
        variantOf(product(UUID.randomUUID(), UUID.randomUUID(), false), variantId, Money.of(1_500_000));
        stock(variantId, available);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, category");
    }

    /** A product of an active category, published or hidden; either may exist already. */
    private UUID product(UUID product, UUID category, boolean published) {
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            on conflict do nothing
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama', ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now(), now())
            on conflict do nothing
            """, product, category, "pijama-" + product, published);
        return product;
    }

    private void variantOf(UUID product, VariantId variantId, Money price) {
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, ?, true, now(), now())
            """, variantId.value(), product, "PP-" + variantId.value().toString().substring(0, 8), price.cents());
    }

    private void stock(VariantId variantId, int available) {
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())",
            variantId.value(), available);
    }
}
