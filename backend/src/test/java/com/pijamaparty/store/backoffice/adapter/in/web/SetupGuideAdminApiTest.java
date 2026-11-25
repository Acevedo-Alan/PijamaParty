package com.pijamaparty.store.backoffice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-46 through the back-office API, with the real security filter chain and PostgreSQL. Settings and catalog rows
 * stored as the owner.
 */
@ApiTest
class SetupGuideAdminApiTest {

    private static final String GUIDE = "/api/admin/setup-guide";
    private static final String OMITTED = GUIDE + "/omitted/";

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    SetupGuideAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    private static RequestPostProcessor admin() {
        return user(UUID.randomUUID().toString()).roles("ADMIN");
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(admin()).with(csrf());
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate setup_guide_omission");
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
        owner.update("""
            update store_settings
            set whatsapp_number = '5493425304507', email = null,
                instagram_url = 'https://www.instagram.com/pijamaparty.zzz/'
            """);
    }

    @Test
    void givenAStoreWithoutWhatsAppAndARopaDeCasaCategoryWithoutAGuide_whenTheGuideIsRead_thenBothArePending()
        throws Exception {
        owner.update("update store_settings set whatsapp_number = null");
        UUID ropaDeCasa = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, 'Ropa de casa', 'ropa-de-casa', 0, true, now(), now())
            """, ropaDeCasa);
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, 'Remerón Stitch', 'remeron-stitch', '', 'Algodón', 'SUMMER', 'KIDS', true, now(), now())
            """, UUID.randomUUID(), ropaDeCasa);

        mvc.perform(get(GUIDE).with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items[0].item").value("WHATSAPP"))
            .andExpect(jsonPath("$.items[0].state").value("PENDING"))
            .andExpect(jsonPath("$.items[1].item").value("EMAIL"))
            .andExpect(jsonPath("$.items[1].state").value("PENDING"))
            .andExpect(jsonPath("$.items[2].item").value("SIZE_GUIDES"))
            .andExpect(jsonPath("$.items[2].state").value("PENDING"))
            .andExpect(jsonPath("$.items[2].total").value(1))
            .andExpect(jsonPath("$.items[2].names[0].id").value(ropaDeCasa.toString()))
            .andExpect(jsonPath("$.items[2].names[0].name").value("Ropa de casa"))
            .andExpect(jsonPath("$.items[3].item").value("PRODUCT_PHOTOS"))
            .andExpect(jsonPath("$.items[3].total").value(1))
            .andExpect(jsonPath("$.items[4].item").value("INSTAGRAM"))
            .andExpect(jsonPath("$.items[4].state").value("DONE"))
            .andExpect(jsonPath("$.items[5].item").value("HOME_BANNER"))
            .andExpect(jsonPath("$.items[5].state").value("PENDING"));
    }

    @Test
    void givenTheBannerOmittedTwiceAndShownAgain_whenTheGuideIsRead_thenItIsOmittedThenPendingAndStoredOnce()
        throws Exception {
        mvc.perform(asAdmin(put(OMITTED + "HOME_BANNER"))).andExpect(status().isNoContent());
        mvc.perform(asAdmin(put(OMITTED + "HOME_BANNER"))).andExpect(status().isNoContent());

        mvc.perform(get(GUIDE).with(admin())).andExpect(jsonPath("$.items[5].state").value("OMITTED"));
        assertThat(owner.queryForObject("select count(*) from setup_guide_omission", Long.class)).isEqualTo(1);

        mvc.perform(asAdmin(delete(OMITTED + "HOME_BANNER"))).andExpect(status().isNoContent());

        mvc.perform(get(GUIDE).with(admin())).andExpect(jsonPath("$.items[5].state").value("PENDING"));
    }

    @Test
    void givenAnItemAWorkingStoreNeeds_whenOmitted_thenItIs409AndNothingIsStored() throws Exception {
        for (String item : new String[] {"WHATSAPP", "EMAIL", "SIZE_GUIDES", "PRODUCT_PHOTOS"}) {
            mvc.perform(asAdmin(put(OMITTED + item)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.problem").value("SETUP_ITEM_NOT_OMITTABLE"));
        }
        assertThat(owner.queryForObject("select count(*) from setup_guide_omission", Long.class)).isZero();
    }

    @Test
    void givenAnUnknownItem_whenOmittedOrShown_thenItIs400() throws Exception {
        mvc.perform(asAdmin(put(OMITTED + "TIKTOK")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(asAdmin(delete(OMITTED + "TIKTOK"))).andExpect(status().isBadRequest());
    }

    @Test
    void givenACustomerOrNobody_whenUsingTheGuide_thenItIsForbiddenOrUnauthorized() throws Exception {
        var customer = user(UUID.randomUUID().toString()).roles("CUSTOMER");
        mvc.perform(get(GUIDE).with(customer)).andExpect(status().isForbidden());
        mvc.perform(put(OMITTED + "HOME_BANNER").with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(OMITTED + "HOME_BANNER").with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(GUIDE)).andExpect(status().isUnauthorized());
        assertThat(owner.queryForObject("select count(*) from setup_guide_omission", Long.class)).isZero();
    }
}
