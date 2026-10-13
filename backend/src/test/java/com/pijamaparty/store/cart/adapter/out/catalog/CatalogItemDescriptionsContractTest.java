package com.pijamaparty.store.cart.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.cart.application.ItemDescriptionsContractTest;
import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL. */
@ApiTest
class CatalogItemDescriptionsContractTest extends ItemDescriptionsContractTest<CatalogItemDescriptions> {

    private final CatalogItemDescriptions adapter;
    private final JdbcTemplate owner;

    CatalogItemDescriptionsContractTest(CatalogItemDescriptions adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogItemDescriptions createSubject() {
        return adapter;
    }

    @Override
    protected void givenVariant(VariantId variantId, String productName, String slug, String size, String color,
                                String sku, boolean sold) {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now(), now())
            """, product, category, productName, slug, sold);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, 0, ?, 0, 1500000, true, now(), now())
            """, variantId.value(), product, sku, size, color);
    }

    @Override
    protected void givenPhotoOfItsColor(VariantId variantId, String storageKey) {
        owner.update("""
            insert into product_image (id, product_id, color, storage_key, placeholder, position, upload_key,
                                       content_sha256, created_at)
            select ?, v.product_id, v.color, ?, ?, 0, ?, ?, now()
            from variant v
            where v.id = ?
            """, UUID.randomUUID(), storageKey, PLACEHOLDER, UUID.randomUUID(), new byte[32], variantId.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, category");
    }
}
