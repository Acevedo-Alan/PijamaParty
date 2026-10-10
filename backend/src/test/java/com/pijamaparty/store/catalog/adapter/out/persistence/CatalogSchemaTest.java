package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
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

/** T-E1-01: the catalog schema (V3) enforces the UC-05 and UC-06 rules, connected as the application role. */
@ApiTest
class CatalogSchemaTest {

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    CatalogSchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, category");
    }

    @Test
    void givenACategorySlugInUse_whenAnotherCategoryTakesIt_thenItIsRejected() {
        category("pijamas");

        assertThatThrownBy(() -> category("pijamas")).isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining("category_slug_uk");
    }

    @Test
    void givenACategoryWithProducts_whenDeletingIt_thenOnlyDeactivatingIsAllowed() {
        var category = category("verano");
        product(category, "short-algodon");

        assertThat(app.update("update category set active = false where id = ?", category)).isOne();
        assertThatThrownBy(() -> app.update("delete from category where id = ?", category))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("product_category_fk");
    }

    @Test
    void givenAVariant_whenAnotherOfTheSameProductHasTheSameSizeAndColor_thenItIsRejected() {
        var product = product(category("invierno"), "pijama-polar");
        variant(product, "SKU-1", "M", "Rosa", null);

        assertThatThrownBy(() -> variant(product, "SKU-2", "M", "Rosa", null))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("variant_product_size_color_uk");
    }

    @Test
    void givenATextWithSurroundingSpacesOrOverItsMaximumLength_whenSaved_thenItIsRejected() {
        var product = product(category("pijamas-largos"), "pijama-a-rayas");

        assertThatThrownBy(() -> variant(product, "SKU-1", "M ", "Rosa", null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("variant_size_length_ck");
        assertThatThrownBy(() -> variant(product, "SKU-2", "M", "R".repeat(31), null))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("variant_color_length_ck");
    }

    @Test
    void givenASku_whenAVariantOfAnotherProductUsesIt_thenItIsRejected() {
        var category = category("mujer");
        variant(product(category, "camison"), "SKU-1", "M", "Rosa", null);

        assertThatThrownBy(() -> variant(product(category, "bata"), "SKU-1", "L", "Celeste", null))
            .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("variant_sku_uk");
    }

    @Test
    void givenAVariantPrice_whenSaved_thenItIsOptionalButNeverZeroOrNegative() {
        var product = product(category("hombre"), "pijama-largo");
        variant(product, "SKU-1", "M", "Gris", null);
        variant(product, "SKU-2", "L", "Gris", 1_999_900L);

        assertThatThrownBy(() -> variant(product, "SKU-3", "XL", "Gris", 0L))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("variant_price_ck");
        assertThatThrownBy(() -> variant(product, "SKU-4", "XXL", "Gris", -100L))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("variant_price_ck");
    }

    @Test
    void givenAPlaceholderThatIsNotABase64WebpDataUri_whenSaved_thenItIsRejected() {
        var product = product(category("accesorios"), "antifaz");

        assertThatThrownBy(() -> app.update("""
                insert into product_image (id, product_id, storage_key, placeholder, position, created_at)
                values (?, ?, 'antifaz-1', 'data:image/webp;base64,AAAA") onerror=alert(1)', 0, now())
                """, UUID.randomUUID(), product))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("product_image_placeholder_ck");
    }

    @Test
    void givenAProduct_whenItsSeasonOrAudienceIsNotOneOfTheEnums_thenItIsRejected() {
        var category = category("ninos");

        assertThatThrownBy(() -> app.update("""
                insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                     created_at, updated_at)
                values (?, ?, 'Pijama', 'pijama', '', 'Algodón', 'SPRING', 'KIDS', false, now(), now())
                """, UUID.randomUUID(), category))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("product_season_ck");
    }

    @Test
    void givenAnImage_whenItsVariantBelongsToAnotherProduct_thenItIsRejected() {
        var category = category("bebes");
        var product = product(category, "enterito");
        var otherVariant = variant(product(category, "body"), "SKU-1", "3M", "Blanco", null);
        image(product, null, "enterito-1");

        assertThatThrownBy(() -> image(product, otherVariant, "enterito-2"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("product_image_variant_fk");
    }

    private UUID category(String slug) {
        var id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, 0, true, now(), now())
            """, id, slug, slug);
        return id;
    }

    private UUID product(UUID category, String slug) {
        var id = UUID.randomUUID();
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', false, now(), now())
            """, id, category, slug, slug);
        return id;
    }

    private UUID variant(UUID product, String sku, String size, String color, Long priceCents) {
        var id = UUID.randomUUID();
        app.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, 0, ?, 0, ?, true, now(), now())
            """, id, product, sku, size, color, priceCents);
        return id;
    }

    private void image(UUID product, UUID variant, String storageKey) {
        app.update("""
            insert into product_image (id, product_id, variant_id, storage_key, placeholder, position, created_at)
            values (?, ?, ?, ?, 'data:image/webp;base64,AAAA', 0, now())
            """, UUID.randomUUID(), product, variant, storageKey);
    }
}
