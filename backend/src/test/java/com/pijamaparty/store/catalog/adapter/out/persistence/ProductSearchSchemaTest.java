package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E1-17: the search schema of UC-26 (V10), used as the application role: the unaccent extension, the search_vector
 * column with its GIN index and the product_search_vector function that weighs the searchable texts.
 */
@ApiTest
class ProductSearchSchemaTest {

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    ProductSearchSchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, size_guide, category");
    }

    @Test
    void givenANewProduct_whenNotIndexedYet_thenItsSearchVectorIsEmpty() {
        var product = product(category("Pijamas"), "pijama-de-algodon", "Pijama de algodón", "", "Algodón");

        assertThat(app.queryForObject("select search_vector::text from product where id = ?", String.class, product))
            .isEmpty();
    }

    @Test
    void givenTheProductTable_whenCheckingItsIndexes_thenTheSearchVectorHasAGinIndex() {
        var definition = owner.queryForObject(
            "select indexdef from pg_indexes where indexname = 'product_search_vector_idx'", String.class);

        assertThat(definition).contains("USING gin (search_vector)");
    }

    @Test
    void givenAProductNamedWithAnAccent_whenSearchingWithoutTheAccentOrInPlural_thenItMatches() {
        var product = indexed(product(category("Pijamas"), "pijama-de-algodon", "Pijama de algodón", "", "Algodón"));

        assertThat(matches(product, "algodon")).isTrue();
        assertThat(matches(product, "PIJAMAS")).isTrue();
        assertThat(matches(product, "polar")).isFalse();
    }

    @Test
    void givenTheSearchableTexts_whenIndexed_thenTheNameWeighsMostAndTheDescriptionLeast() {
        var category = category("Invierno");
        var product = product(category, "pijama-polar", "Pijama polar", "Abrigado y suave, ideal con medias.", "Modal");
        variant(product, "SKU-1", "Bordó", true);
        indexed(product);

        assertThat(matchesWithWeight(product, "polar", "A")).isTrue();
        assertThat(matchesWithWeight(product, "invierno", "B")).isTrue();
        assertThat(matchesWithWeight(product, "modal", "B")).isTrue();
        assertThat(matchesWithWeight(product, "medias", "C")).isTrue();
        assertThat(matchesWithWeight(product, "bordo", "C")).isTrue();
        assertThat(matchesWithWeight(product, "medias", "AB")).isFalse();
    }

    @Test
    void givenAnInactiveVariant_whenIndexed_thenOnlyTheColorsOfActiveVariantsCount() {
        var product = product(category("Mujer"), "camison", "Camisón", "", "Algodón");
        variant(product, "SKU-1", "Rosa", true);
        variant(product, "SKU-2", "Celeste", false);
        indexed(product);

        assertThat(matches(product, "rosa")).isTrue();
        assertThat(matches(product, "celeste")).isFalse();
    }

    @Test
    void givenTextWithQuotesAndSymbols_whenParsedAsAQuery_thenItIsPlainTextAndCausesNoError() {
        var product = indexed(product(category("Pijamas"), "pijama-de-algodon", "Pijama de algodón", "", "Algodón"));

        assertThat(matches(product, "\"algodón' or 1=1; --")).isFalse();
        assertThat(matches(product, "\"pijama de algodon\"")).isTrue();
    }

    private boolean matches(UUID product, String text) {
        return Boolean.TRUE.equals(app.queryForObject("""
            select search_vector @@ websearch_to_tsquery('spanish', unaccent(?)) from product where id = ?
            """, Boolean.class, text, product));
    }

    /** A one-word tsquery restricted to the given weights, for example {@code medias:C}. */
    private boolean matchesWithWeight(UUID product, String word, String weights) {
        return Boolean.TRUE.equals(app.queryForObject("""
            select search_vector @@ to_tsquery('spanish', unaccent(?) || ':' || ?) from product where id = ?
            """, Boolean.class, word, weights, product));
    }

    private UUID indexed(UUID product) {
        app.update("update product set search_vector = product_search_vector(id) where id = ?", product);
        return product;
    }

    private UUID category(String name) {
        var id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, 0, true, now(), now())
            """, id, name, "c-" + id);
        return id;
    }

    private UUID product(UUID category, String slug, String name, String description, String material) {
        var id = UUID.randomUUID();
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, 'WINTER', 'WOMEN', true, now(), now())
            """, id, category, name, slug, description, material);
        return id;
    }

    private void variant(UUID product, String sku, String color, boolean active) {
        app.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, ?, 0, 1000000, ?, now(), now())
            """, UUID.randomUUID(), product, sku, color, active);
    }
}
