package com.pijamaparty.store.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.UUID;
import java.util.stream.IntStream;
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

/** UC-06 through the back office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class ProductAdminApiTest {

    private static final String PRODUCTS = "/api/admin/catalog/products";
    private static final String VARIANTS = "/api/admin/catalog/variants";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private String category;

    ProductAdminApiTest(MockMvc mvc, ObjectMapper json, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() throws Exception {
        category = json.readTree(send(post("/api/admin/catalog/categories"), "{\"name\":\"Verano\"}")
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("""
            truncate product_image, variant, product, size_guide, category, stock_movement, stock_level,
                     activity_entry
            """);
    }

    @Test
    void givenAVariantPriceChange_whenSaved_thenActividadShowsTheSkuAndBothPrices_andARefusedOneShowsNothing()
        throws Exception {
        String id = idOf(create("Pijama"));
        JsonNode variant = body(generate(id, List.of("M"), List.of("Rosa"))).at("/variants/0");
        String variantId = variant.get("id").asText();
        send(patch(VARIANTS + "/" + variantId), "{\"priceCents\":2000000}").andExpect(status().isOk());

        send(patch(VARIANTS + "/" + variantId), "{\"priceCents\":2200000}").andExpect(status().isOk());
        send(patch(VARIANTS + "/" + variantId), "{\"priceCents\":0}").andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/admin/activity").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].action").value("VARIANT_UPDATED"))
            .andExpect(jsonPath("$.items[0].subject.id").value(variantId))
            .andExpect(jsonPath("$.items[0].subject.label").value(variant.get("sku").asText()))
            .andExpect(jsonPath("$.items[0].changes[0].field").value("price"))
            .andExpect(jsonPath("$.items[0].changes[0].before").value("$20.000"))
            .andExpect(jsonPath("$.items[0].changes[0].after").value("$22.000"));
        assertThat(owner.queryForObject("select count(*) from activity_entry where action = 'VARIANT_UPDATED'",
            Integer.class)).isEqualTo(2);
    }

    @Test
    void givenDetails_whenTheAdministratorCreatesAProduct_thenItIsHiddenWithoutVariants() throws Exception {
        create(" Pijama de Algodón ")
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("Pijama de Algodón"))
            .andExpect(jsonPath("$.slug").value("pijama-de-algodon"))
            .andExpect(jsonPath("$.categoryId").value(category))
            .andExpect(jsonPath("$.season").value("SUMMER"))
            .andExpect(jsonPath("$.audience").value("WOMEN"))
            .andExpect(jsonPath("$.published").value(false))
            .andExpect(jsonPath("$.variants.length()").value(0));
    }

    @Test
    void given3SizesAnd2Colors_whenTheMatrixIsGenerated_then6VariantsAreCreatedWithTheirStock() throws Exception {
        String id = idOf(create("Pijama"));

        generate(id, List.of("S", "M", "L"), List.of("Rosa", "Celeste"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sizes", contains("S", "M", "L")))
            .andExpect(jsonPath("$.colors", contains("Rosa", "Celeste")))
            .andExpect(jsonPath("$.variants.length()").value(6))
            .andExpect(jsonPath("$.variants[*].sku", everyItem(matchesPattern("PP-[0-9A-HJKMNP-TV-Z]{8}"))))
            .andExpect(jsonPath("$.variants[*].priceCents", everyItem(nullValue())))
            .andExpect(jsonPath("$.variants[0].size").value("S"))
            .andExpect(jsonPath("$.variants[0].color").value("Rosa"));

        assertThat(owner.queryForObject("""
            select count(*) from stock_level s join variant v on v.id = s.variant_id
            where v.product_id = ? and s.on_hand = 0
            """, Integer.class, UUID.fromString(id))).isEqualTo(6);
        generate(id, List.of("S", "M", "L"), List.of("Rosa", "Celeste"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(6));
    }

    @Test
    void givenAHiddenProduct_whenPublished_thenItAppearsInTheCatalogAsPublished() throws Exception {
        String id = idOf(create("Pijama"));
        JsonNode product = body(generate(id, List.of("M"), List.of("Rosa")));
        String variant = product.at("/variants/0/id").asText();

        mvc.perform(post(PRODUCTS + "/" + id + "/publish").with(ADMIN).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Product cannot be published"));
        send(patch(VARIANTS + "/" + variant), "{\"priceCents\":1999900}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[0].priceCents").value(1999900));

        mvc.perform(post(PRODUCTS + "/" + id + "/publish").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));

        mvc.perform(get(PRODUCTS).with(ADMIN))
            .andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.items[0].published").value(true))
            .andExpect(jsonPath("$.items[0].variantCount").value(1))
            .andExpect(jsonPath("$.hasMore").value(false));
        assertThat(app.queryForObject("select active from product where id = ?", Boolean.class, UUID.fromString(id)))
            .isTrue();
    }

    @Test
    void givenAPublishedProduct_whenItsLastPricedVariantIsDeactivated_thenItIsRejectedUntilHidden() throws Exception {
        String id = idOf(create("Pijama"));
        String variant = body(generate(id, List.of("M"), List.of("Rosa"))).at("/variants/0/id").asText();
        send(patch(VARIANTS + "/" + variant), "{\"priceCents\":1500000}").andExpect(status().isOk());
        mvc.perform(post(PRODUCTS + "/" + id + "/publish").with(ADMIN).with(csrf())).andExpect(status().isOk());

        send(patch(VARIANTS + "/" + variant), "{\"active\":false}")
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Last active priced variant"));

        mvc.perform(post(PRODUCTS + "/" + id + "/hide").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(false));
        send(patch(VARIANTS + "/" + variant), "{\"active\":false}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[0].active").value(false));
    }

    @Test
    void givenAProduct_whenEdited_thenItsSlugFollowsTheNameUnlessAnotherProductUsesIt() throws Exception {
        create("Camisón");
        String id = idOf(create("Pijama"));

        send(put(PRODUCTS + "/" + id), productJson("Pijama de Seda"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("pijama-de-seda"));
        send(put(PRODUCTS + "/" + id), productJson("CAMISÓN"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Slug already used"))
            .andExpect(jsonPath("$.slug").value("camison"));
        mvc.perform(get(PRODUCTS + "/" + id).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pijama de Seda"));
        send(put(PRODUCTS + "/" + UUID.randomUUID()), productJson("Bata")).andExpect(status().isNotFound());
        mvc.perform(get(PRODUCTS + "/" + UUID.randomUUID()).with(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenInvalidInput_whenManagingProducts_thenItIsRejectedAsProblemJson() throws Exception {
        String id = idOf(create("Pijama"));

        send(post(PRODUCTS), productJson(" ¡! ", "Algo\ndón"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid product details"))
            .andExpect(jsonPath("$.fields", contains("MATERIAL", "NAME")));
        send(post(PRODUCTS), productJson("a".repeat(121)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("NAME")));
        send(post(PRODUCTS), productJson("a".repeat(120) + " ")).andExpect(status().isCreated());
        send(post(PRODUCTS), productJson("a".repeat(481))).andExpect(status().isBadRequest());
        send(post(PRODUCTS), productJson("Bata").replace("SUMMER", "SPRING")).andExpect(status().isBadRequest());
        send(post(PRODUCTS), productJson("Bata").replace(category, UUID.randomUUID().toString()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Unknown category"));
        generate(id, List.of("M", "M"), List.of("Rosa"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Invalid variant matrix"))
            .andExpect(jsonPath("$.problem").value("REPEATED_SIZES"));
        generate(id, List.of("a".repeat(21)), List.of("Rosa"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("INVALID_SIZE"));
        generate(id, IntStream.rangeClosed(1, 21).mapToObj(i -> "T" + i).toList(), List.of("Rosa"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("TOO_MANY_SIZES"));
        generate(id, List.of("a".repeat(81)), List.of("Rosa")).andExpect(status().isBadRequest());
        String variant = body(generate(id, List.of("M"), List.of("Rosa"))).at("/variants/0/id").asText();
        send(patch(VARIANTS + "/" + variant), "{\"priceCents\":0}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Invalid price"));
        send(patch(VARIANTS + "/" + UUID.randomUUID()), "{\"priceCents\":100}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Variant not found"));
        mvc.perform(get(PRODUCTS).param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenManagingProducts_thenAccessIsDenied() throws Exception {
        mvc.perform(get(PRODUCTS)).andExpect(status().isUnauthorized());
        mvc.perform(get(PRODUCTS).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(PRODUCTS).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(productJson("Bata")))
            .andExpect(status().isForbidden());
        mvc.perform(get(PRODUCTS).with(ADMIN)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void givenNoCsrfToken_whenTheAdministratorChangesProducts_thenEveryChangeIsRefused() throws Exception {
        String id = idOf(create("Pijama"));
        String variant = body(generate(id, List.of("M"), List.of("Rosa"))).at("/variants/0/id").asText();
        List<MockHttpServletRequestBuilder> changes = List.of(
            post(PRODUCTS).content(productJson("Bata")),
            put(PRODUCTS + "/" + id).content(productJson("Bata")),
            post(PRODUCTS + "/" + id + "/variants/generate").content("{\"sizes\":[\"L\"],\"colors\":[\"Rosa\"]}"),
            post(PRODUCTS + "/" + id + "/publish"),
            post(PRODUCTS + "/" + id + "/hide"),
            patch(VARIANTS + "/" + variant).content("{\"priceCents\":100}"));

        for (MockHttpServletRequestBuilder change : changes) {
            mvc.perform(change.with(ADMIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get(PRODUCTS + "/" + id).with(ADMIN))
            .andExpect(jsonPath("$.name").value("Pijama"))
            .andExpect(jsonPath("$.variants.length()").value(1))
            .andExpect(jsonPath("$.variants[0].priceCents").value(nullValue()));
    }

    private ResultActions create(String name) throws Exception {
        return send(post(PRODUCTS), productJson(name));
    }

    private ResultActions generate(String id, List<String> sizes, List<String> colors) throws Exception {
        return send(post(PRODUCTS + "/" + id + "/variants/generate"),
            json.writeValueAsString(new ProductAdminController.MatrixRequest(sizes, colors)));
    }

    private String productJson(String name) throws Exception {
        return productJson(name, "Algodón");
    }

    private String productJson(String name, String material) throws Exception {
        return json.writeValueAsString(new ProductAdminController.ProductRequest(UUID.fromString(category), name,
            "Suave y fresco.", material, Season.SUMMER,
            Audience.WOMEN));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String idOf(ResultActions created) throws Exception {
        return body(created).get("id").asText();
    }
}
