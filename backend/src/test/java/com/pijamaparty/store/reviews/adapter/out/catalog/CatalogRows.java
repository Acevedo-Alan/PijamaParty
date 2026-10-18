package com.pijamaparty.store.reviews.adapter.out.catalog;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test arrangement: catalog rows stored as the owner, for the adapters of the reviews module. */
final class CatalogRows {

    private CatalogRows() {
    }

    /** A product of its own category, published or not, without variants. */
    static void product(JdbcTemplate owner, UUID product, String name, boolean published) {
        product(owner, product, name, "producto-" + product, published);
    }

    /** A product at {@code slug} of its own category, published or not, without variants. */
    static void product(JdbcTemplate owner, UUID product, String name, String slug, boolean published) {
        UUID category = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', ?, 0, true, now(), now())
            """, category, "verano-" + category);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now(), now())
            """, product, category, name, slug, published);
    }

    /** A variant of the product, which the catalog sells or no longer sells. */
    static void variant(JdbcTemplate owner, UUID product, UUID variant, boolean sold) {
        owner.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, 0, 'Rosa', 0, 1500000, ?, now(), now())
            """, variant, product, "SKU-" + variant, "T-" + variant.toString().substring(0, 8), sold);
    }

    static void cleanUp(JdbcTemplate owner) {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
    }
}
