package com.pijamaparty.store.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * RNF-31.5: the size guide through the back office API and the product page, with the real security filter chain and
 * PostgreSQL.
 */
@ApiTest
class SizeGuideApiTest {

    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final String WOMEN_GUIDE = """
        {"columns": ["Busto (cm)", " Cintura (cm) "],
         "rows": [{"size": "M", "values": ["88–93", "70–75"]}, {"size": "S", "values": ["83–87", " "]}],
         "note": "Medí sobre la ropa interior."}
        """;

    private final MockMvc mvc;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private UUID women;

    SizeGuideApiTest(MockMvc mvc, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() {
        women = category("pijamas-de-mujer");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate size_guide, stock_movement, stock_level, product_image, variant, product, category");
    }

    @Test
    void givenACategoryWithAGuide_whenOneOfItsProductsOpens_thenTheGuideShowsColumnsRowsInOrderAndTheNote()
        throws Exception {
        listedProduct(women, "pijama-rosa");

        save(women, WOMEN_GUIDE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns", contains("Busto (cm)", "Cintura (cm)")))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(get("/api/catalog/products/pijama-rosa"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sizeGuide.columns", contains("Busto (cm)", "Cintura (cm)")))
            .andExpect(jsonPath("$.sizeGuide.rows[*].size", contains("M", "S")))
            .andExpect(jsonPath("$.sizeGuide.rows[0].values", contains("88–93", "70–75")))
            .andExpect(jsonPath("$.sizeGuide.rows[1].values", contains("83–87", "")))
            .andExpect(jsonPath("$.sizeGuide.note").value("Medí sobre la ropa interior."))
            .andExpect(jsonPath("$.sizeGuide.updatedAt").doesNotExist());
    }

    @Test
    void givenACategoryWithoutAGuide_whenOneOfItsProductsOpens_thenItHasNoGuide() throws Exception {
        listedProduct(women, "pijama-rosa");

        mvc.perform(get("/api/catalog/products/pijama-rosa"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sizeGuide").value(nullValue()));
        mvc.perform(get(guideOf(women)).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.id").value(women.toString()))
            .andExpect(jsonPath("$.category.name").value("pijamas-de-mujer"))
            .andExpect(jsonPath("$.guide").value(nullValue()));
    }

    @Test
    void givenAGuideWithARowThatLacksAValue_whenSaved_thenItIsRefusedAndNothingChanges() throws Exception {
        save(women, WOMEN_GUIDE).andExpect(status().isOk());

        save(women, """
            {"columns": ["Busto", "Cintura"], "rows": [{"size": "S", "values": ["83–87"]}]}
            """)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_SIZE_GUIDE"))
            .andExpect(jsonPath("$.fields", contains("rows")));

        mvc.perform(get(guideOf(women)).with(ADMIN))
            .andExpect(jsonPath("$.guide.columns", contains("Busto (cm)", "Cintura (cm)")));
    }

    @Test
    void givenTwoColumnsWithTheSameNameInDifferentCase_whenSaved_thenItIsRefused() throws Exception {
        save(women, """
            {"columns": ["Busto", "BUSTO"], "rows": [{"size": "S", "values": ["1", "2"]}], "note": "%s"}
            """.formatted("n".repeat(501)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("columns", "note")));

        mvc.perform(get(guideOf(women)).with(ADMIN)).andExpect(jsonPath("$.guide").value(nullValue()));
    }

    @Test
    void givenACategoryWithAGuide_whenTheAdministratorDeletesIt_thenItsProductsNoLongerShowIt() throws Exception {
        listedProduct(women, "pijama-rosa");
        save(women, WOMEN_GUIDE).andExpect(status().isOk());

        mvc.perform(delete(guideOf(women)).with(ADMIN).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(guideOf(women)).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/api/catalog/products/pijama-rosa")).andExpect(jsonPath("$.sizeGuide").value(nullValue()));
    }

    @Test
    void givenACategoryWithoutProductsAndWithAGuide_whenTheCategoryIsDeleted_thenItsGuideIsDeletedToo()
        throws Exception {
        save(women, WOMEN_GUIDE).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/catalog/categories/" + women).with(ADMIN).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get(guideOf(women)).with(ADMIN)).andExpect(status().isNotFound());
        assertNoGuides();
    }

    @Test
    void givenAnUnknownCategory_whenItsGuideIsOpenedSavedOrDeleted_thenItIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get(guideOf(unknown)).with(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        save(unknown, WOMEN_GUIDE).andExpect(status().isNotFound());
        mvc.perform(delete(guideOf(unknown)).with(ADMIN).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void givenSomeoneWhoIsNotTheAdministrator_whenItTouchesAGuide_thenItIsRefused() throws Exception {
        RequestPostProcessor customer = user(UUID.randomUUID().toString()).roles("CUSTOMER");

        mvc.perform(get(guideOf(women))).andExpect(status().isUnauthorized());
        mvc.perform(get(guideOf(women)).with(customer)).andExpect(status().isForbidden());
        mvc.perform(put(guideOf(women)).with(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(WOMEN_GUIDE)).andExpect(status().isForbidden());
        mvc.perform(put(guideOf(women)).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(WOMEN_GUIDE))
            .andExpect(status().isForbidden());
        assertNoGuides();
    }

    @Test
    void givenAMalformedBody_whenSaved_thenItIsABadRequest() throws Exception {
        save(women, "{\"columns\": [\"Busto\"]}").andExpect(status().isBadRequest());
        save(women, "{\"columns\": [null], \"rows\": []}").andExpect(status().isBadRequest());
    }

    private ResultActions save(UUID category, String body) throws Exception {
        return mvc.perform(put(guideOf(category)).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private void assertNoGuides() {
        assertThat(owner.queryForObject("select count(*) from size_guide",
            Integer.class)).isZero();
    }

    private static String guideOf(UUID category) {
        return "/api/admin/catalog/categories/" + category + "/size-guide";
    }

    private UUID category(String slug) {
        UUID id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, 0, true, now(), now())
            """, id, slug, slug);
        return id;
    }

    /** A published product with an active priced variant in stock. */
    private void listedProduct(UUID category, String slug) {
        UUID product = UUID.randomUUID();
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', true, now(), now())
            """, product, category, slug, slug);
        UUID variant = UUID.randomUUID();
        app.update("""
            insert into variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                                 created_at, updated_at)
            values (?, ?, ?, 'M', 0, 'Rosa', 0, 4500, true, now(), now())
            """, variant, product, "PP-" + variant.toString().substring(0, 8));
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 3, now())", variant);
    }
}
