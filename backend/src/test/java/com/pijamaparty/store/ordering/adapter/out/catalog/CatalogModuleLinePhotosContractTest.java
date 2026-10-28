package com.pijamaparty.store.ordering.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.LinePhotosContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL. */
@ApiTest
class CatalogModuleLinePhotosContractTest extends LinePhotosContractTest<CatalogModuleLinePhotos> {

    private final CatalogModuleLinePhotos adapter;
    private final JdbcTemplate owner;

    CatalogModuleLinePhotosContractTest(CatalogModuleLinePhotos adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogModuleLinePhotos createSubject() {
        return adapter;
    }

    @Override
    protected void givenVariant(UUID variantId, boolean sold) {
        UUID category = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Pijama Luna', ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now(), now())
            """, product, category, "pijama-luna-" + product, sold);
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 1500000, true, now(), now())
            """, variantId, product, "PL-" + variantId.toString().substring(0, 8));
    }

    @Override
    protected void givenPhotoOfItsColor(UUID variantId, String storageKey) {
        owner.update("""
            insert into product_image (id, product_id, color, storage_key, placeholder, position, upload_key,
                                       content_sha256, created_at)
            select ?, v.product_id, v.color, ?, ?, 0, ?, ?, now()
            from variant v
            where v.id = ?
            """, UUID.randomUUID(), storageKey, PLACEHOLDER, UUID.randomUUID(), new byte[32], variantId);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
    }
}
