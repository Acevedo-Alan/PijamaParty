package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-07 (T-E8-09): the "Stock" screen through the back office API, with the real security chain and PostgreSQL. */
@ApiTest
class StockOverviewAdminApiTest {

    private static final String STOCK = "/api/admin/catalog/stock";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private String category;

    StockOverviewAdminApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() throws Exception {
        category = body(send(post("/api/admin/catalog/categories"), "{\"name\":\"Verano\"}")).get("id").asText();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("""
            truncate product_image, variant, product, size_guide, category, stock_movement, stock_level,
                     activity_entry
            """);
    }

    @Test
    void givenVariantsWith0_2And5AndAThresholdOf3_whenFiltered_thenLowIsThe2AndOutTheSoldOne() throws Exception {
        JsonNode product = generate(create("Pijama Luna"), List.of("S", "M", "L"));
        setStock(product, 0, 0, 0);
        setStock(product, 1, 4, 2);
        setStock(product, 2, 5, 0);

        mvc.perform(get(STOCK).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].size", contains("S", "M", "L")))
            .andExpect(jsonPath("$.items[1].productName").value("Pijama Luna"))
            .andExpect(jsonPath("$.items[1].onHand").value(4))
            .andExpect(jsonPath("$.items[1].reserved").value(2))
            .andExpect(jsonPath("$.items[1].available").value(2))
            .andExpect(jsonPath("$.items[1].published").value(false))
            .andExpect(jsonPath("$.items[1].sku").value(product.at("/variants/1/sku").asText()))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(STOCK).param("filter", "LOW").with(ADMIN))
            .andExpect(jsonPath("$.items[*].size", contains("M")));
        mvc.perform(get(STOCK).param("filter", "OUT").with(ADMIN))
            .andExpect(jsonPath("$.items[*].size", contains("S")));
    }

    @Test
    void givenAnAdjustmentSavedFromTheScreen_whenTheListIsReadAgain_thenTheRowHasTheNewUnits() throws Exception {
        JsonNode product = generate(create("Pijama Luna"), List.of("M"));
        String variant = product.at("/variants/0/id").asText();
        String key = UUID.randomUUID().toString();
        String adjustment = "{\"variantId\":\"" + variant + "\",\"quantity\":3,\"note\":\"Llegó el pedido\"}";

        adjust(key, adjustment).andExpect(status().isCreated()).andExpect(jsonPath("$.onHand").value(3));
        adjust(key, adjustment).andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(3));

        mvc.perform(get(STOCK).with(ADMIN))
            .andExpect(jsonPath("$.items[0].onHand").value(3))
            .andExpect(jsonPath("$.items[0].available").value(3));
        mvc.perform(get("/api/admin/variants/" + variant + "/stock-movements").with(ADMIN))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].note").value("Llegó el pedido"));
    }

    @Test
    void givenASearchAndACategory_whenListed_thenTheNameOrTheSkuMatchesWithinTheCategory() throws Exception {
        JsonNode product = generate(create("Pijama Corazón"), List.of("S", "M"));
        String sku = product.at("/variants/1/sku").asText();

        mvc.perform(get(STOCK).param("q", "corazon").with(ADMIN))
            .andExpect(jsonPath("$.items[*].size", contains("S", "M")));
        mvc.perform(get(STOCK).param("q", sku.toLowerCase(Locale.ROOT)).with(ADMIN))
            .andExpect(jsonPath("$.items[*].size", contains("M")));
        mvc.perform(get(STOCK).param("category", category).with(ADMIN))
            .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get(STOCK).param("category", UUID.randomUUID().toString()).with(ADMIN))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenInvalidFilters_whenListed_thenTheyAreRejectedAsProblemJson() throws Exception {
        mvc.perform(get(STOCK).param("q", "a").with(ADMIN))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INVALID_SEARCH"));
        mvc.perform(get(STOCK).param("filter", "SOME").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(STOCK).param("category", "verano").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get(STOCK).param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenListing_thenAccessIsDenied() throws Exception {
        mvc.perform(get(STOCK)).andExpect(status().isUnauthorized());
        mvc.perform(get(STOCK).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    private String create(String name) throws Exception {
        String product = json.writeValueAsString(new ProductAdminController.ProductRequest(UUID.fromString(category),
            name, "Suave y fresco.", "Algodón", Season.SUMMER, Audience.WOMEN));
        return body(send(post("/api/admin/catalog/products"), product)).get("id").asText();
    }

    /** Generates the sizes in Rosa; the variants come back by size in the order given. */
    private JsonNode generate(String id, List<String> sizes) throws Exception {
        return body(send(post("/api/admin/catalog/products/" + id + "/variants/generate"),
            json.writeValueAsString(new ProductAdminController.MatrixRequest(sizes, List.of("Rosa")))));
    }

    private void setStock(JsonNode product, int variant, int onHand, int reserved) {
        owner.update("update stock_level set on_hand = ?, reserved = ? where variant_id = ?", onHand, reserved,
            UUID.fromString(product.at("/variants/" + variant + "/id").asText()));
    }

    private ResultActions adjust(String key, String body) throws Exception {
        return mvc.perform(post("/api/admin/stock-adjustments").header("Idempotency-Key", key).with(ADMIN)
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
