package com.pijamaparty.store.settings.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.settings.adapter.out.persistence.StoreSettingsRow;
import java.util.List;
import java.util.Map;
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

/** UC-36 through the public and the back-office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class StoreSettingsApiTest {

    private static final String SETTINGS = "/api/admin/store/settings";
    private static final String CONTACT = "/api/store/contact";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    StoreSettingsApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    // Other tests may have changed the row; activity entries of other tests are not read here.
    @BeforeEach
    @AfterEach
    void cleanUp() {
        StoreSettingsRow.reset(owner);
        owner.execute("delete from activity_entry where action = 'STORE_SETTINGS_CHANGED'");
    }

    private static String body(String whatsApp, String instagram, String announcement, long threshold,
                               long version) {
        return """
            {"email":null,"whatsAppNumber":%s,"openingHours":"Lunes a viernes de 9 a 18 h",
             "address":"San Martín 2000, Santa Fe","instagramUrl":%s,"facebookUrl":"https://www.facebook.com/pijamaparty",
             "tiktokUrl":"","announcement":%s,"lowStockThreshold":%d,"version":%d}
            """.formatted(json(whatsApp), json(instagram), json(announcement), threshold, version);
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put(SETTINGS).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void givenANewStore_whenTheAdministratorAndAVisitorReadIt_thenTheyGetTheBusinessWhatsAppAndInstagram()
        throws Exception {
        mvc.perform(get(SETTINGS).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.whatsAppNumber").value("5493425304507"))
            .andExpect(jsonPath("$.instagramUrl").value("https://www.instagram.com/pijamaparty.zzz/"))
            .andExpect(jsonPath("$.email").isEmpty())
            .andExpect(jsonPath("$.lowStockThreshold").value(3))
            .andExpect(jsonPath("$.version").value(0));
        mvc.perform(get(CONTACT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.whatsAppNumber").value("5493425304507"))
            .andExpect(jsonPath("$.tiktokUrl").isEmpty())
            .andExpect(jsonPath("$.announcement").isEmpty())
            .andExpect(jsonPath("$.lowStockThreshold").doesNotExist())
            .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void givenAWhatsAppNumberWithSpacesAndDashes_whenSaved_thenTheStoreGetsItsDigitsAndTheNewValues()
        throws Exception {
        save(body("+54 9 342 400-0000", "https://instagram.com/pijamaparty", "Envío gratis a todo Santa Fe", 5, 0))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.whatsAppNumber").value("5493424000000"))
            .andExpect(jsonPath("$.tiktokUrl").isEmpty())
            .andExpect(jsonPath("$.lowStockThreshold").value(5))
            .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get(CONTACT))
            .andExpect(jsonPath("$.whatsAppNumber").value("5493424000000"))
            .andExpect(jsonPath("$.openingHours").value("Lunes a viernes de 9 a 18 h"))
            .andExpect(jsonPath("$.address").value("San Martín 2000, Santa Fe"))
            .andExpect(jsonPath("$.facebookUrl").value("https://www.facebook.com/pijamaparty"))
            .andExpect(jsonPath("$.tiktokUrl").isEmpty())
            .andExpect(jsonPath("$.announcement").value("Envío gratis a todo Santa Fe"));
    }

    @Test
    void givenASave_whenItChangesValues_thenActivityHasOneEntryWithTheChangedFields() throws Exception {
        save(body("5493425304507", "https://www.instagram.com/pijamaparty.zzz/", null, 3, 0))
            .andExpect(status().isOk());

        List<Map<String, Object>> entries = owner.queryForList("""
            select area, subject_type, changes::text as changes from activity_entry
            where action = 'STORE_SETTINGS_CHANGED'
            """);
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.get("area")).isEqualTo("SETTINGS");
            assertThat(entry.get("subject_type")).isEqualTo("STORE_SETTINGS");
            assertThat((String) entry.get("changes"))
                .contains("openingHours", "address", "facebookUrl")
                .doesNotContain("whatsAppNumber", "instagramUrl", "lowStockThreshold");
        });
    }

    @Test
    void givenLinksThatCouldLeadElsewhereAndAThresholdOutOfRange_whenSaved_thenEachIsNamedAndNothingIsSaved()
        throws Exception {
        for (String link : List.of("https://evil.example/instagram.com", "http://instagram.com/x",
            "https://instagram.com.evil.example/x")) {
            save(body(null, link, null, 3, 0))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.problem").value("INVALID_SETTINGS"))
                .andExpect(jsonPath("$.fields", contains("instagramUrl")));
        }
        save(body(null, null, null, 1001, 0))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("lowStockThreshold")));
        save(body(null, null, null, -1, 0))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("lowStockThreshold")));
        save(body(null, null, "Envío gratis\\na todo el país", 3, 0))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("announcement")));

        mvc.perform(get(SETTINGS).with(ADMIN)).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void givenTwoTabsThatReadTheSameVersion_whenTheSecondSaves_thenItGets409AndNothingOfItIsSaved()
        throws Exception {
        save(body("5493424000000", null, null, 3, 0)).andExpect(status().isOk());

        save(body("5493424111111", null, "Otra pestaña", 9, 0))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("SETTINGS_CHANGED"));

        mvc.perform(get(SETTINGS).with(ADMIN))
            .andExpect(jsonPath("$.whatsAppNumber").value("5493424000000"))
            .andExpect(jsonPath("$.lowStockThreshold").value(3))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void givenAVisitorOrACustomer_whenSaving_thenItIsUnauthorizedOrForbidden() throws Exception {
        mvc.perform(put(SETTINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(null, null, null, 3, 0)))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(SETTINGS).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(null, null, null, 3, 0)))
            .andExpect(status().isForbidden());
        mvc.perform(get(SETTINGS)).andExpect(status().isUnauthorized());
    }

    @Test
    void givenNoVersion_whenSaved_thenItIsABadRequest() throws Exception {
        save("{\"lowStockThreshold\":3}").andExpect(status().isBadRequest());
    }
}
