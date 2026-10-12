package com.pijamaparty.store.shipping.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.shipping.domain.Province;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
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

/** UC-12 through the back office API, with the real security filter chain and PostgreSQL. */
@ApiTest
class ShippingAdminApiTest {

    private static final String SHIPPING = "/api/admin/shipping";
    private static final String ZONES = SHIPPING + "/zones";
    private static final String FREE_SHIPPING = SHIPPING + "/free-shipping";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    ShippingAdminApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
        this.mvc = mvc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate shipping_zone_rate");
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
    }

    @Test
    void givenNothingConfigured_whenTheAdministratorReadsIt_thenEveryProvinceIsListedWithoutShipping()
        throws Exception {
        mvc.perform(get(SHIPPING).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.zones[*].province",
                contains(Arrays.stream(Province.values()).map(Enum::name).toArray())))
            .andExpect(jsonPath("$.zones[*].costCents", everyItem(nullValue())))
            .andExpect(jsonPath("$.freeShippingThresholdCents").isEmpty());
    }

    @Test
    void givenCosts_whenTheAdministratorReplacesThem_thenOnlyTheListedProvincesHaveShipping() throws Exception {
        send(put(ZONES), """
            {"zones":[{"province":"MENDOZA","costCents":900000},{"province":"SANTA_FE","costCents":300000}]}
            """).andExpect(status().isOk());

        send(put(ZONES), """
            {"zones":[{"province":"SANTA_FE","costCents":0},{"province":"CORDOBA","costCents":650000}]}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones[?(@.province == 'SANTA_FE')].costCents", contains(0)))
            .andExpect(jsonPath("$.zones[?(@.province == 'CORDOBA')].costCents", contains(650_000)))
            .andExpect(jsonPath("$.zones[?(@.province == 'MENDOZA')].costCents", contains(nullValue())))
            .andExpect(jsonPath("$.zones.length()").value(24));
        mvc.perform(get(SHIPPING).with(ADMIN))
            .andExpect(jsonPath("$.zones[?(@.province == 'SANTA_FE')].costCents", contains(0)))
            .andExpect(jsonPath("$.zones[?(@.province == 'MENDOZA')].costCents", contains(nullValue())));

        send(put(ZONES), "{\"zones\":[]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones[*].costCents", everyItem(nullValue())));
    }

    @Test
    void givenInvalidCosts_whenReplacing_thenTheProvincesAreNamedAsProblemJsonAndNothingChanges() throws Exception {
        send(put(ZONES), "{\"zones\":[{\"province\":\"MENDOZA\",\"costCents\":900000}]}");

        send(put(ZONES), """
            {"zones":[{"province":"SANTA_FE","costCents":-1},{"province":"CORDOBA","costCents":1000000001},
                      {"province":"SALTA","costCents":100},{"province":"SALTA","costCents":200},
                      {"province":"JUJUY","costCents":100}]}
            """)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid shipping costs"))
            .andExpect(jsonPath("$.provinces", contains("CORDOBA", "SALTA", "SANTA_FE")));

        send(put(ZONES), "{}").andExpect(status().isBadRequest());
        send(put(ZONES), "{\"zones\":[{\"province\":\"Santa Fe\",\"costCents\":100}]}")
            .andExpect(status().isBadRequest());
        send(put(ZONES), "{\"zones\":[{\"province\":\"SANTA_FE\"}]}").andExpect(status().isBadRequest());
        send(put(ZONES), "{\"zones\":[null]}").andExpect(status().isBadRequest());
        String tooMany = Arrays.stream(Province.values()).map(p -> "{\"province\":\"" + p + "\",\"costCents\":1}")
            .collect(Collectors.joining(",", "{\"zones\":[", ",{\"province\":\"SALTA\",\"costCents\":1}]}"));
        send(put(ZONES), tooMany).andExpect(status().isBadRequest());
        mvc.perform(get(SHIPPING).with(ADMIN))
            .andExpect(jsonPath("$.zones[?(@.costCents != null)].province", contains("MENDOZA")));
    }

    @Test
    void givenAThreshold_whenTheAdministratorSetsAndThenRemovesIt_thenEachIsShown() throws Exception {
        send(put(FREE_SHIPPING), "{\"thresholdCents\":5000000}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.freeShippingThresholdCents").value(5_000_000))
            .andExpect(jsonPath("$.zones.length()").value(24));
        mvc.perform(get(SHIPPING).with(ADMIN)).andExpect(jsonPath("$.freeShippingThresholdCents").value(5_000_000));

        send(put(FREE_SHIPPING), "{\"thresholdCents\":null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.freeShippingThresholdCents").isEmpty());
    }

    @Test
    void givenAThresholdOutOfRange_whenSet_thenItIsRefusedAsProblemJson() throws Exception {
        send(put(FREE_SHIPPING), "{\"thresholdCents\":0}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid free-shipping threshold"));
        send(put(FREE_SHIPPING), "{\"thresholdCents\":1000000001}").andExpect(status().isUnprocessableEntity());
        send(put(FREE_SHIPPING), "{\"thresholdCents\":\"mucho\"}").andExpect(status().isBadRequest());
        mvc.perform(get(SHIPPING).with(ADMIN)).andExpect(jsonPath("$.freeShippingThresholdCents").isEmpty());
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenConfiguringShipping_thenAccessIsDenied() throws Exception {
        mvc.perform(get(SHIPPING)).andExpect(status().isUnauthorized());
        mvc.perform(get(SHIPPING).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(put(FREE_SHIPPING).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"thresholdCents\":100}"))
            .andExpect(status().isForbidden());
        mvc.perform(get(SHIPPING).with(ADMIN)).andExpect(jsonPath("$.freeShippingThresholdCents").isEmpty());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
