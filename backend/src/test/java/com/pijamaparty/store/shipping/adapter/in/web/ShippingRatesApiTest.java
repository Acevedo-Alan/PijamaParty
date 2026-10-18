package com.pijamaparty.store.shipping.adapter.in.web;

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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/** RNF-31.6 (T-E5-12): the public shipping costs, with the real security filter chain and PostgreSQL. */
@ApiTest
class ShippingRatesApiTest {

    private static final String RATES = "/api/shipping/rates";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");

    private final MockMvc mvc;
    private final JdbcTemplate owner;

    ShippingRatesApiTest(MockMvc mvc, PostgreSQLContainer<?> postgres) {
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
    void givenNothingConfigured_whenAVisitorReadsTheRates_thenNoProvinceIsListedNorFreeShipping() throws Exception {
        mvc.perform(get(RATES))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.zones").isEmpty())
            .andExpect(jsonPath("$.freeShippingThresholdCents").isEmpty());
    }

    @Test
    void givenCostsForSantaFeAndCordobaAndAThreshold_whenAVisitorReadsTheRates_thenOnlyThoseAreListedInOrder()
        throws Exception {
        mvc.perform(put("/api/admin/shipping/zones").with(ADMIN)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"zones\":[{\"province\":\"SANTA_FE\",\"costCents\":350000},"
                    + "{\"province\":\"CORDOBA\",\"costCents\":0}]}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/shipping/free-shipping").with(ADMIN)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"thresholdCents\":5000000}"))
            .andExpect(status().isOk());

        mvc.perform(get(RATES))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones[*].province", contains("CORDOBA", "SANTA_FE")))
            .andExpect(jsonPath("$.zones[*].costCents", contains(0, 350_000)))
            .andExpect(jsonPath("$.freeShippingThresholdCents").value(5_000_000));
    }

    @Test
    void givenACustomer_whenItReadsTheRates_thenItGetsTheSameAnswer() throws Exception {
        mvc.perform(get(RATES).with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones").isEmpty());
    }
}
