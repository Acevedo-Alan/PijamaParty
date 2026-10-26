package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    /** {@code store.public-url} in the tests: the default of application.yml. */
    private static final String STOREFRONT = "http://localhost:5173";

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
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
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
            .andExpect(jsonPath("$.items[1].image.files[0].url").value("/uploads/" + storageKey + "-400.webp"))
            .andExpect(jsonPath("$.items[1].secondImage").value(nullValue()));
        String secondKey = image(withStock, null, 1);
        mvc.perform(get(PRODUCTS))
            .andExpect(jsonPath("$.items[1].image.files[0].url").value("/uploads/" + storageKey + "-400.webp"))
            .andExpect(jsonPath("$.items[1].secondImage.placeholder").value(PLACEHOLDER))
            .andExpect(jsonPath("$.items[1].secondImage.files[0].url").value("/uploads/" + secondKey + "-400.webp"));
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
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.total").value(25));
        mvc.perform(get(PRODUCTS).param("page", "1"))
            .andExpect(jsonPath("$.items[*].name", contains("Pijama 0")))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.total").value(25));
        mvc.perform(get(PRODUCTS).param("size", "S"))
            .andExpect(jsonPath("$.items", hasSize(0)))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void givenColorsWithAndWithoutStockAndCreationDates_whenListed_thenEachItemHasItsColorsInStockAndWhetherItIsNew()
        throws Exception {
        UUID recent = product(summer, "Reciente", "SUMMER", true);
        variant(recent, "M", "Rosa", 1000, 2);
        variant(recent, "L", "Beige", 1000, 0);
        UUID old = product(summer, "Viejo", "SUMMER", true);
        variant(old, "M", "Rosa", 1000, 1);
        app.update("update product set created_at = now() - interval '10 days' where id = ?", recent);
        app.update("update product set created_at = now() - interval '40 days' where id = ?", old);
        app.update("update product set search_vector = product_search_vector(id) where id = ?", recent);

        mvc.perform(get(PRODUCTS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains("Reciente", "Viejo")))
            .andExpect(jsonPath("$.items[0].colors", contains("Rosa")))
            .andExpect(jsonPath("$.items[0].isNew").value(true))
            .andExpect(jsonPath("$.items[1].isNew").value(false));
        mvc.perform(get(PRODUCTS).param("q", "reciente"))
            .andExpect(jsonPath("$.items[0].colors", contains("Rosa")))
            .andExpect(jsonPath("$.items[0].isNew").value(true))
            .andExpect(jsonPath("$.total").value(1));
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
            .andExpect(jsonPath("$.category.slug").value("verano"))
            .andExpect(jsonPath("$.category.name").value("verano"))
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
    void givenAListedProductWithAPhotoAndTwoPrices_whenItsPreviewIsRequested_thenTheTagsCarryItsNamePricePhotoAndUrl()
        throws Exception {
        String description = "Pijama de algodón peinado, suave y fresco para las noches de verano. ".repeat(4).strip();
        UUID pajama = product(summer, "Pijama rosa", "pijama-rosa", description);
        variant(pajama, "M", "Rosa", 1_250_000, 3);
        variant(pajama, "L", "Rosa", 1_400_050, 0);
        String photo = image(pajama);
        image(pajama, "Rosa", 1);
        String summary = "Desde $ 12.500. Pijama de algodón peinado, suave y fresco para las noches de verano. Pijama "
            + "de algodón peinado, suave y fresco para las noches de verano. Pijama de algodón peinado, suave y fresco "
            + "para las noches de…";

        mvc.perform(get(PRODUCTS + "/pijama-rosa/preview"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(content().string(containsString("<title>Pijama rosa | Pijama Party Santa Fe</title>")))
            .andExpect(content().string(containsString(meta("og:title", "Pijama rosa | Pijama Party Santa Fe"))))
            .andExpect(content().string(containsString(meta("og:site_name", "Pijama Party Santa Fe"))))
            .andExpect(content().string(containsString(meta("og:type", "product"))))
            .andExpect(content().string(containsString(meta("og:locale", "es_AR"))))
            .andExpect(content().string(containsString(meta("og:description", summary))))
            .andExpect(content().string(containsString("<meta name=\"description\" content=\"" + summary + "\">")))
            .andExpect(content().string(containsString(meta("og:url", STOREFRONT + "/producto/pijama-rosa"))))
            .andExpect(content().string(containsString(
                "<link rel=\"canonical\" href=\"" + STOREFRONT + "/producto/pijama-rosa\">")))
            .andExpect(content().string(containsString(
                meta("og:image", STOREFRONT + "/uploads/" + photo + "-1200.webp"))))
            .andExpect(content().string(containsString(meta("og:image:alt", "Pijama rosa"))))
            .andExpect(content().string(containsString(meta("product:price:amount", "12500.00"))))
            .andExpect(content().string(containsString(meta("product:price:currency", "ARS"))))
            .andExpect(content().string(containsString(
                "<meta name=\"twitter:card\" content=\"summary_large_image\">")));
    }

    @Test
    void givenOnePriceAndNoPhoto_whenItsPreviewIsRequested_thenThePriceHasNoDesdeAndThereIsNoImage() throws Exception {
        UUID nightgown = product(summer, "Camisón", "camison", "");
        variant(nightgown, "M", "Rosa", 1_250_050, 3);
        variant(nightgown, "L", "Rosa", 1_250_050, 3);

        mvc.perform(get(PRODUCTS + "/camison/preview"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(meta("og:description", "$ 12.500,50"))))
            .andExpect(content().string(containsString(meta("product:price:amount", "12500.50"))))
            .andExpect(content().string(not(containsString("og:image"))))
            .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary\">")));
    }

    @Test
    void givenANameAndDescriptionWithMarkup_whenTheirPreviewIsRendered_thenTheyAreEscaped() throws Exception {
        UUID pajama = product(summer, "Pijama \"Noche\" <b>", "pijama-noche", "Talle M & L <script>alert('x')</script>");
        variant(pajama, "M", "Rosa", 1_000_000, 3);

        mvc.perform(get(PRODUCTS + "/pijama-noche/preview"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<b>"))))
            .andExpect(content().string(not(containsString("<script>"))))
            .andExpect(content().string(containsString("<h1>Pijama &quot;Noche&quot; &lt;b&gt;</h1>")))
            .andExpect(content().string(containsString(meta("og:description",
                "$ 10.000. Talle M &amp; L &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"))));
    }

    @Test
    void givenAHiddenOrUnknownProduct_whenItsPreviewIsRequested_thenTheStoreIsPreviewedWith404() throws Exception {
        variant(product(summer, "Oculto", "SUMMER", false), "M", "Rosa", 1000, 5);

        for (String slug : new String[] {"oculto", "no-existe"}) {
            mvc.perform(get(PRODUCTS + "/" + slug + "/preview"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>Pijama Party Santa Fe</title>")))
                .andExpect(content().string(containsString(meta("og:title", "Pijama Party Santa Fe"))))
                .andExpect(content().string(containsString(meta("og:url", STOREFRONT + "/"))))
                .andExpect(content().string(not(containsString("Oculto"))));
        }
    }

    @Test
    void givenListedHiddenAndUnsellableProductsAndAnInactiveCategory_whenTheSitemapIsRequested_thenOnlyIndexablePages()
        throws Exception {
        UUID winter = category("invierno", false);
        variant(product(summer, "Pijama", "SUMMER", true), "M", "Rosa", 1000, 0);
        variant(product(summer, "Oculto", "SUMMER", false), "M", "Rosa", 1000, 5);
        product(summer, "Sin variantes", "SUMMER", true);
        variant(product(winter, "Abrigado", "WINTER", true), "M", "Rosa", 1000, 5);

        mvc.perform(get("/api/catalog/sitemap"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andExpect(content().string("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                <url><loc>%1$s/</loc></url>
                <url><loc>%1$s/preguntas-frecuentes</loc></url>
                <url><loc>%1$s/como-comprar</loc></url>
                <url><loc>%1$s/cambios-y-devoluciones</loc></url>
                <url><loc>%1$s/terminos</loc></url>
                <url><loc>%1$s/categoria/verano</loc></url>
                <url><loc>%1$s/producto/pijama</loc></url>
                </urlset>
                """.formatted(STOREFRONT)));
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
        String slug = name.toLowerCase().replace("ó", "o").replace(' ', '-');
        return product(category, name, slug, "", season, active);
    }

    /** An active summer product with this slug and description. */
    private UUID product(UUID category, String name, String slug, String description) {
        return product(category, name, slug, description, "SUMMER", true);
    }

    private UUID product(UUID category, String name, String slug, String description, String season, boolean active) {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, ?, 'Algodón', ?, 'WOMEN', ?, timestamptz '2026-10-01 12:00Z' + ? * interval '1 minute',
                    now())
            """, id, category, name, slug, description, season, active, minutes++);
        return id;
    }

    /** An Open Graph tag as the preview writes it. */
    private static String meta(String property, String content) {
        return "<meta property=\"" + property + "\" content=\"" + content + "\">";
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
