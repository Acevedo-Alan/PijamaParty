package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-26: the back office writes keep the search index current through the catalog events, end to end: what the
 * administrator saves is what a visitor finds.
 */
@ApiTest
class SearchIndexApiTest {

    private static final String ADMIN_CATALOG = "/api/admin/catalog";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;

    SearchIndexApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, category, stock_movement, stock_level");
    }

    @Test
    void givenAProductSavedInTheBackOffice_whenItsTextsChange_thenTheSearchFollowsThem() throws Exception {
        String category = id(send(post(ADMIN_CATALOG + "/categories"), "{\"name\":\"Verano\"}"));
        String product = id(send(post(ADMIN_CATALOG + "/products"), details(category, "Pijama de algodón")));
        JsonNode generated = body(send(post(ADMIN_CATALOG + "/products/" + product + "/variants/generate"),
            "{\"sizes\":[\"M\"],\"colors\":[\"Rosa\",\"Celeste\"]}"));
        for (JsonNode variant : generated.get("variants")) {
            send(patch(ADMIN_CATALOG + "/variants/" + variant.get("id").asText()), "{\"priceCents\":450000}")
                .andExpect(status().isOk());
        }
        mvc.perform(post(ADMIN_CATALOG + "/products/" + product + "/publish").with(ADMIN).with(csrf()))
            .andExpect(status().isOk());

        expectFound("algodon", "Pijama de algodón");
        expectFound("celeste", "Pijama de algodón");
        mvc.perform(get("/api/catalog/suggestions").param("q", "pija"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains("Pijama de algodón")));

        String celeste = generated.at("/variants/1/id").asText();
        send(patch(ADMIN_CATALOG + "/variants/" + celeste), "{\"active\":false}").andExpect(status().isOk());
        expectNothing("celeste");

        send(patch(ADMIN_CATALOG + "/categories/" + category), "{\"name\":\"Noche\"}").andExpect(status().isOk());
        expectFound("noche", "Pijama de algodón");
        expectNothing("verano");

        send(put(ADMIN_CATALOG + "/products/" + product), details(category, "Camisón de seda"))
            .andExpect(status().isOk());
        expectFound("seda", "Camisón de seda");
        expectNothing("pijama");
    }

    private void expectFound(String text, String name) throws Exception {
        mvc.perform(get("/api/catalog/products").param("q", text))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name", contains(name)));
    }

    private void expectNothing(String text) throws Exception {
        mvc.perform(get("/api/catalog/products").param("q", text))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    private String details(String category, String name) {
        return """
            {"categoryId":"%s","name":"%s","description":"Suave.","material":"Modal","season":"SUMMER",
             "audience":"WOMEN"}
            """.formatted(category, name);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions result) throws Exception {
        return body(result).get("id").asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
