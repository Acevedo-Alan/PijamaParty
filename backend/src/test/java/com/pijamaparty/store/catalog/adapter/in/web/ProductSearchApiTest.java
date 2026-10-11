package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The acceptance criteria of UC-26 (T-E1-20) through the public API, as an anonymous visitor, on PostgreSQL with the
 * real spanish configuration. Rows are stored directly and indexed with the same SQL function the application uses;
 * that the back office keeps the index current is {@link SearchIndexApiTest}. The criterion of more than 60 searches
 * per minute is {@code RateLimitPoliciesTest.givenSixtySearchesInAMinuteFromOneIp_whenMoreArrive_thenTheyGet429...},
 * and the message with links to the categories is shown by the storefront ({@code SearchPage.test.tsx}) from the
 * empty result and {@code GET /api/catalog/categories} checked here.
 */
@ApiTest
class ProductSearchApiTest {

    private static final String PRODUCTS = "/api/catalog/products";
    private static final String SUGGESTIONS = "/api/catalog/suggestions";

    private final MockMvc mvc;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private UUID summer;
    private int minutes;

    ProductSearchApiTest(MockMvc mvc, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() {
        summer = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Verano', 'verano', 0, true, now(), now())
            """, summer);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, category");
    }

    @Test
    void givenPijamaDeAlgodon_whenSearchingAlgodonWithoutTheAccent_thenItAppears() throws Exception {
        listed("Pijama de algodón", "", "Modal", "M", 1);
        listed("Camisón", "", "Modal", "M", 1);

        expectNames("algodon", "Pijama de algodón");
        expectNames("ALGODÓN", "Pijama de algodón");
    }

    @Test
    void givenPijamaDeAlgodon_whenSearchingThePlural_thenItAppears() throws Exception {
        listed("Pijama de algodón", "", "Modal", "M", 1);

        expectNames("pijamas", "Pijama de algodón");
    }

    @Test
    void givenMatchesByNameAndOnlyByDescription_whenSearching_thenTheNameMatchesComeFirst() throws Exception {
        listed("Camisón de seda", "", "Modal", "M", 1);
        listed("Pijama clásico", "Tela de seda suave.", "Modal", "M", 1);

        expectNames("seda", "Camisón de seda", "Pijama clásico");
    }

    @Test
    void givenNoMatches_whenSearching_thenTheResultIsEmptyAndTheCategoriesCanBeLinked() throws Exception {
        listed("Pijama", "", "Modal", "M", 1);

        mvc.perform(get(PRODUCTS).param("q", "zapatillas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/catalog/categories"))
            .andExpect(jsonPath("$.items[*].slug", contains("verano")))
            .andExpect(jsonPath("$.items[*].name", contains("Verano")));
    }

    @Test
    void givenASearchWithSizeM_whenApplied_thenOnlyProductsWithAnMVariantInStockAppear() throws Exception {
        listed("Pijama talle M", "", "Modal", "M", 1);
        UUID onlyS = listed("Pijama talle S", "", "Modal", "S", 3);
        variant(onlyS, "M", 0);
        refresh(onlyS);

        mvc.perform(get(PRODUCTS).param("q", "pijama").param("size", "M"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains("Pijama talle M")));
    }

    @Test
    void givenAnInactiveProduct_whenSearchingItsName_thenItDoesNotAppear() throws Exception {
        UUID hidden = product("Pijama oculto", "", "Modal", false);
        variant(hidden, "M", 5);
        refresh(hidden);

        expectNames("oculto");
        mvc.perform(get(SUGGESTIONS).param("q", "ocul"))
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void givenQuotesSymbolsOrInjectionAttempts_whenSearching_thenTheyAreTreatedAsPlainText() throws Exception {
        listed("Pijama", "", "Modal", "M", 1);

        for (String text : new String[] {"'; drop table product; --", "\" or 1=1 --", "pijama:* & !(", "\\\\",
            "<script>", "%_", "pijama\u0000"}) {
            mvc.perform(get(PRODUCTS).param("q", text))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
            mvc.perform(get(SUGGESTIONS).param("q", text)).andExpect(status().isOk());
        }
        expectNames("\"pijama", "Pijama");
        expectNames("pijama:* & !(", "Pijama");
        expectNames("'; drop table product; --");
        expectNames("pijama", "Pijama");
    }

    @Test
    void givenTypedText_whenSuggesting_thenUpTo5ListedProductsWithPhotoAndNameMatchTheStartOfTheLastWord()
        throws Exception {
        for (int i = 1; i <= 6; i++) {
            listed("Pijama " + i, "", "Modal", "M", 1);
        }

        mvc.perform(get(SUGGESTIONS).param("q", "pija"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains("Pijama 6", "Pijama 5", "Pijama 4", "Pijama 3",
                "Pijama 2")))
            .andExpect(jsonPath("$.items[0].slug").value("pijama-6"))
            .andExpect(jsonPath("$.items[0].image").value(nullValue()));
        mvc.perform(get(SUGGESTIONS).param("q", "p")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void givenATextShorterThan2CharactersOrTooLong_whenSearching_thenItIsRejected() throws Exception {
        mvc.perform(get(PRODUCTS).param("q", " p "))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(PRODUCTS).param("q", "a".repeat(401))).andExpect(status().isBadRequest());
        mvc.perform(get(PRODUCTS).param("q", "pijama").param("sort", "RELEVANCE")).andExpect(status().isBadRequest());
    }

    private void expectNames(String text, String... names) throws Exception {
        mvc.perform(get(PRODUCTS).param("q", text))
            .andExpect(status().isOk())
            .andExpect(names.length == 0
                ? jsonPath("$.items", hasSize(0))
                : jsonPath("$.items[*].name", contains(names)));
    }

    /** A published product with one active priced variant of the size, indexed. Each is a minute newer. */
    private UUID listed(String name, String description, String material, String size, int onHand) {
        UUID id = product(name, description, material, true);
        variant(id, size, onHand);
        refresh(id);
        return id;
    }

    private UUID product(String name, String description, String material, boolean active) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace("ó", "o").replace("á", "a").replace(' ', '-');
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, 'SUMMER', 'WOMEN', ?, timestamptz '2026-10-01 12:00Z' + ? * interval '1 minute',
                    now())
            """, id, summer, name, slug, description, material, active, minutes++);
        return id;
    }

    private void variant(UUID product, String size, int onHand) {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, 0, 'Rosa', 0, 100000, true, now(), now())
            """, id, product, "PP-" + id.toString().substring(0, 8), size);
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", id, onHand);
    }

    /** What {@code SearchIndexListener} runs after a product write. */
    private void refresh(UUID product) {
        app.update("update product set search_vector = product_search_vector(id) where id = ?", product);
    }
}
