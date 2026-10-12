package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
 * UC-01 to UC-03 through the public API, as an anonymous visitor, with the real security filter chain and PostgreSQL.
 */
@ApiTest
class CatalogApiTest {

    private static final String PRODUCTS = "/api/catalog/products";
    private static final String FILTERS = "/api/catalog/filters";
    private static final String PLACEHOLDER = "data:image/webp;base64,UklGRg==";

    private final MockMvc mvc;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private UUID summer;
    private int minutes;

    CatalogApiTest(MockMvc mvc, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() {
        summer = category("verano", true);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, category");
    }

    @Test
    void givenListedHiddenAndSoldOutProducts_whenAVisitorListsTheCatalog_thenHiddenOnesDoNotAppearAndSoldOutOnesSaySo()
        throws Exception {
        UUID withStock = product(summer, "Pijama rosa", "SUMMER", true);
        UUID rosaM = variant(withStock, "M", "Rosa", 4500, 2);
        variant(withStock, "L", "Rosa", 5200, 0);
        String storageKey = image(withStock);
        UUID soldOut = product(summer, "Camisón", "SUMMER", true);
        variant(soldOut, "M", "Rosa", 3000, 0);
        UUID hidden = product(summer, "Oculto", "SUMMER", false);
        variant(hidden, "M", "Rosa", 1000, 5);

        mvc.perform(get(PRODUCTS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items[*].name", contains("Camisón", "Pijama rosa")))
            .andExpect(jsonPath("$.items[0].soldOut").value(true))
            .andExpect(jsonPath("$.items[0].image").value(nullValue()))
            .andExpect(jsonPath("$.items[1].slug").value("pijama-rosa"))
            .andExpect(jsonPath("$.items[1].fromPriceCents").value(4500))
            .andExpect(jsonPath("$.items[1].soldOut").value(false))
            .andExpect(jsonPath("$.items[1].image.placeholder").value(PLACEHOLDER))
            .andExpect(jsonPath("$.items[1].image.files[*].width", contains(400, 800, 1200)))
            .andExpect(jsonPath("$.items[1].image.files[0].url").value("/uploads/" + storageKey + "-400.webp"));
        app.update("update stock_level set on_hand = 0 where variant_id = ?", rosaM);
        mvc.perform(get(PRODUCTS)).andExpect(jsonPath("$.items[1].soldOut").value(true));
    }

    @Test
    void givenMoreThan24Matches_whenTheCatalogOpens_then24AreShownAndMoreFollow() throws Exception {
        for (int i = 0; i < 25; i++) {
            variant(product(summer, "Pijama " + i, "SUMMER", true), "M", "Rosa", 1000, 1);
        }

        mvc.perform(get(PRODUCTS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(24)))
            .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get(PRODUCTS).param("page", "1"))
            .andExpect(jsonPath("$.items[*].name", contains("Pijama 0")))
            .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void givenSizeMAndSeasonSummer_whenApplied_thenOnlyProductsWithAnMVariantInStockAndSeasonSummerAppear()
        throws Exception {
        UUID match = product(summer, "Verano M", "SUMMER", true);
        variant(match, "M", "Rosa", 1000, 1);
        UUID winter = product(summer, "Invierno M", "WINTER", true);
        variant(winter, "M", "Rosa", 1000, 1);
        UUID noStockInM = product(summer, "Verano sin M", "SUMMER", true);
        variant(noStockInM, "M", "Rosa", 1000, 0);
        variant(noStockInM, "S", "Rosa", 1000, 3);

        mvc.perform(get(PRODUCTS).param("size", "M").param("season", "SUMMER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains("Verano M")));
    }

    @Test
    void givenAColorWithAComma_whenFiltered_thenItIsOneValue() throws Exception {
        UUID product = product(summer, "Pijama", "SUMMER", true);
        variant(product, "M", "Rosa, gris", 1000, 1);

        mvc.perform(get(PRODUCTS).param("color", "Rosa, gris"))
            .andExpect(jsonPath("$.items[*].name", contains("Pijama")));
        mvc.perform(get(PRODUCTS).param("color", "Rosa"))
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void givenSortsAndPriceRanges_whenApplied_thenTheyUseTheFromPrice() throws Exception {
        variant(product(summer, "Barato", "SUMMER", true), "M", "Rosa", 1000, 1);
        variant(product(summer, "Caro", "SUMMER", true), "M", "Rosa", 9000, 1);
        variant(product(summer, "Medio", "SUMMER", true), "M", "Rosa", 5000, 1);

        mvc.perform(get(PRODUCTS).param("sort", "PRICE_ASC"))
            .andExpect(jsonPath("$.items[*].name", contains("Barato", "Medio", "Caro")));
        mvc.perform(get(PRODUCTS).param("sort", "PRICE_DESC").param("minPrice", "5000").param("maxPrice", "9000"))
            .andExpect(jsonPath("$.items[*].name", contains("Caro", "Medio")));
    }

    @Test
    void givenAnUnknownOrInactiveCategory_whenListed_thenItIsNotFound() throws Exception {
        category("cerrada", false);
        variant(product(summer, "Pijama", "SUMMER", true), "M", "Rosa", 1000, 1);

        mvc.perform(get(PRODUCTS).param("category", "verano"))
            .andExpect(jsonPath("$.items[*].name", contains("Pijama")));
        mvc.perform(get(PRODUCTS).param("category", "cerrada"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(FILTERS).param("category", "no-existe"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenInvalidParameters_whenListed_thenTheyAreRejected() throws Exception {
        mvc.perform(get(PRODUCTS).param("sort", "name; drop table product"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(PRODUCTS).param("season", "SPRING")).andExpect(status().isBadRequest());
        mvc.perform(get(PRODUCTS).param("minPrice", "2000").param("maxPrice", "1000"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(PRODUCTS).param("page", "1001")).andExpect(status().isBadRequest());
        mvc.perform(get(PRODUCTS).param("size", "x".repeat(21))).andExpect(status().isBadRequest());
    }

    @Test
    void givenListedProducts_whenAVisitorOpensTheFilters_thenTheOptionsOfTheShopAreShown() throws Exception {
        UUID product = product(summer, "Pijama", "SUMMER", true);
        variant(product, "S", "Rosa", 2500, 0);
        variant(product, "M", "Celeste", 4000, 1);

        mvc.perform(get(FILTERS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sizes", contains("M")))
            .andExpect(jsonPath("$.colors", contains("Celeste")))
            .andExpect(jsonPath("$.materials", contains("Algodón")))
            .andExpect(jsonPath("$.seasons", contains("SUMMER")))
            .andExpect(jsonPath("$.audiences", contains("WOMEN")))
            .andExpect(jsonPath("$.minPriceCents").value(2500))
            .andExpect(jsonPath("$.maxPriceCents").value(2500));
    }

    @Test
    void givenPinkMInStockAndLightBlueMOutOfStock_whenAVisitorOpensTheDetail_thenOnlyLightBlueMHasNothingAvailable()
        throws Exception {
        UUID pajama = product(summer, "Pijama rosa", "SUMMER", true);
        UUID pinkM = variant(pajama, "M", "Rosa", 4500, 3);
        variant(pajama, "M", "Celeste", 5200, 0);
        String whole = image(pajama);
        String pink = image(pajama, "Rosa", 1);

        mvc.perform(get(PRODUCTS + "/pijama-rosa"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("pijama-rosa"))
            .andExpect(jsonPath("$.name").value("Pijama rosa"))
            .andExpect(jsonPath("$.material").value("Algodón"))
            .andExpect(jsonPath("$.season").value("SUMMER"))
            .andExpect(jsonPath("$.audience").value("WOMEN"))
            .andExpect(jsonPath("$.fromPriceCents").value(4500))
            .andExpect(jsonPath("$.sizes", contains("M")))
            .andExpect(jsonPath("$.colors", containsInAnyOrder("Rosa", "Celeste")))
            .andExpect(jsonPath("$.variants", hasSize(2)))
            .andExpect(jsonPath("$.variants[?(@.color == 'Rosa')].id", contains(pinkM.toString())))
            .andExpect(jsonPath("$.variants[?(@.color == 'Rosa')].sku", contains(sku(pinkM))))
            .andExpect(jsonPath("$.variants[?(@.color == 'Rosa')].available", contains(3)))
            .andExpect(jsonPath("$.variants[?(@.color == 'Celeste')].available", contains(0)))
            .andExpect(jsonPath("$.variants[?(@.color == 'Celeste')].priceCents", contains(5200)))
            .andExpect(jsonPath("$.images[*].color", contains(nullValue(), is("Rosa"))))
            .andExpect(jsonPath("$.images[0].placeholder").value(PLACEHOLDER))
            .andExpect(jsonPath("$.images[0].files[0].url").value("/uploads/" + whole + "-400.webp"))
            .andExpect(jsonPath("$.images[1].files[2].url").value("/uploads/" + pink + "-1200.webp"));
    }

    @Test
    void givenSlugsOfNoListedProduct_whenOpened_thenTheyAreNotFound() throws Exception {
        variant(product(summer, "Oculto", "SUMMER", false), "M", "Rosa", 1000, 5);
        UUID closed = category("cerrada", false);
        variant(product(closed, "De cerrada", "SUMMER", true), "M", "Rosa", 1000, 5);

        for (String slug : new String[] {"oculto", "de-cerrada", "no-existe", "No-Existe"}) {
            mvc.perform(get(PRODUCTS + "/" + slug))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get(PRODUCTS + "/" + "a".repeat(561)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenActiveAndInactiveCategories_whenAVisitorListsThem_thenOnlyTheActiveOnesAppear() throws Exception {
        category("invierno", true);
        category("cerrada", false);

        mvc.perform(get("/api/catalog/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].slug", containsInAnyOrder("verano", "invierno")))
            .andExpect(jsonPath("$.items[?(@.slug == 'invierno')].name", contains("invierno")));
    }

    private UUID category(String slug, boolean active) {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, 0, ?, now(), now())
            """, id, slug, slug, active);
        return id;
    }

    /** Each product is a minute newer than the previous one. */
    private UUID product(UUID category, String name, String season, boolean active) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace("ó", "o").replace(' ', '-');
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', ?, 'WOMEN', ?, timestamptz '2026-10-01 12:00Z' + ? * interval '1 minute',
                    now())
            """, id, category, name, slug, season, active, minutes++);
        return id;
    }

    /** An active priced variant with its stock level. */
    private UUID variant(UUID product, String size, String color, long cents, int onHand) {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, 0, ?, 0, ?, true, now(), now())
            """, id, product, sku(id), size, color, cents);
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", id, onHand);
        return id;
    }

    /** The SKU {@link #variant} gives the variant with this id. */
    private static String sku(UUID variant) {
        return "PP-" + variant.toString().substring(0, 8);
    }

    private String image(UUID product) {
        return image(product, null, 0);
    }

    /** A photo of one color, or of the whole product when {@code color} is null. */
    private String image(UUID product, String color, int position) {
        String storageKey = UUID.randomUUID().toString();
        app.update("""
            insert into product_image (id, product_id, color, storage_key, placeholder, position, upload_key,
                                       content_sha256, created_at)
            values (?, ?, ?, ?, ?, ?, gen_random_uuid(), sha256(?::bytea), now())
            """, UUID.randomUUID(), product, color, storageKey, PLACEHOLDER, position, storageKey);
        return storageKey;
    }
}
