package com.pijamaparty.store.catalog.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** UC-05 through the back office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class CategoryAdminApiTest {

    private static final String CATEGORIES = "/api/admin/catalog/categories";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    CategoryAdminApiTest(MockMvc mvc, ObjectMapper json, JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.json = json;
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, category");
    }

    @Test
    void givenAName_whenTheAdministratorCreatesACategory_thenItIsActiveLastWithASlugFromTheName() throws Exception {
        create("Verano").andExpect(status().isCreated());

        create(" Pijamas de Niñas ")
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("Pijamas de Niñas"))
            .andExpect(jsonPath("$.slug").value("pijamas-de-ninas"))
            .andExpect(jsonPath("$.position").value(1))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenAnExistingCategoryName_whenAnotherWithTheSameSlugIsCreated_thenItIsRejectedWithAClearMessage()
        throws Exception {
        create("Pijamas de Niñas").andExpect(status().isCreated());

        create("PIJAMAS DE NIÑAS")
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Slug already used"))
            .andExpect(jsonPath("$.detail").value("Another category already uses the slug of this name."))
            .andExpect(jsonPath("$.slug").value("pijamas-de-ninas"));
        mvc.perform(get(CATEGORIES).with(ADMIN)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void givenACategoryWithProducts_whenDeletionIsAttempted_thenOnlyDeactivationIsAllowed() throws Exception {
        String id = idOf(create("Verano"));
        givenAProductIn(id);

        mvc.perform(delete(CATEGORIES + "/" + id).with(ADMIN).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Category has products"))
            .andExpect(jsonPath("$.detail").value("A category with products cannot be deleted; deactivate it instead."));

        mvc.perform(post(CATEGORIES + "/" + id + "/deactivate").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(CATEGORIES).with(ADMIN))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void givenACategoryWithoutProducts_whenDeleted_thenItIsGone() throws Exception {
        String id = idOf(create("Verano"));

        mvc.perform(delete(CATEGORIES + "/" + id).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(delete(CATEGORIES + "/" + id).with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(CATEGORIES).with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void givenADeactivatedCategory_whenActivated_thenItIsActiveAgain() throws Exception {
        String id = idOf(create("Verano"));
        mvc.perform(post(CATEGORIES + "/" + id + "/deactivate").with(ADMIN).with(csrf())).andExpect(status().isOk());

        mvc.perform(post(CATEGORIES + "/" + id + "/activate").with(ADMIN).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenACategory_whenRenamed_thenItsSlugFollowsTheNewNameUnlessAnotherCategoryUsesIt() throws Exception {
        create("Verano");
        String id = idOf(create("Invierno"));

        send(patch(CATEGORIES + "/" + id), "{\"name\":\"Pijamas de invierno\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("pijamas-de-invierno"));
        send(patch(CATEGORIES + "/" + id), "{\"name\":\"verano\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.slug").value("verano"));
        send(patch(CATEGORIES + "/" + UUID.randomUUID()), "{\"name\":\"Otoño\"}")
            .andExpect(status().isNotFound());
    }

    @Test
    void givenAnInvalidName_whenCreatingOrRenaming_thenItIsRejectedAsProblemJson() throws Exception {
        create("¡¿?!")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid category name"));
        create("a".repeat(81))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        send(post(CATEGORIES), "{}").andExpect(status().isBadRequest());
        send(patch(CATEGORIES + "/not-a-uuid"), "{\"name\":\"Otoño\"}").andExpect(status().isBadRequest());
    }

    @Test
    void givenEveryCategoryInANewOrder_whenReordering_thenTheListFollowsIt() throws Exception {
        String summer = idOf(create("Verano"));
        String winter = idOf(create("Invierno"));

        send(put(CATEGORIES + "/order"), "{\"categoryIds\":[\"" + winter + "\",\"" + summer + "\"]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(winter, summer)))
            .andExpect(jsonPath("$[*].position", contains(0, 1)));
        mvc.perform(get(CATEGORIES).with(ADMIN)).andExpect(jsonPath("$[*].id", contains(winter, summer)));

        send(put(CATEGORIES + "/order"), "{\"categoryIds\":[\"" + winter + "\"]}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenManagingCategories_thenAccessIsDenied() throws Exception {
        mvc.perform(get(CATEGORIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(CATEGORIES).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(CATEGORIES).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Verano\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get(CATEGORIES).with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions create(String name) throws Exception {
        return send(post(CATEGORIES), json.writeValueAsString(new CategoryAdminController.NameRequest(name)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String idOf(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void givenAProductIn(String category) {
        app.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Short de algodón', 'short-de-algodon', '', 'Algodón', 'SUMMER', 'WOMEN', false, now(), now())
            """, UUID.randomUUID(), UUID.fromString(category));
    }
}
