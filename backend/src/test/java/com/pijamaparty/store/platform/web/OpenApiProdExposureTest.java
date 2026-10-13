package com.pijamaparty.store.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ApiTest
@ActiveProfiles("prod")
// In production the migrate container runs Flyway; this test database has no such step, so the test migrates itself.
@TestPropertySource(properties = {"spring.flyway.enabled=true", "PUBLIC_URL=https://pijamaparty.test"})
class OpenApiProdExposureTest {

    private final MockMvc mvc;

    OpenApiProdExposureTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenTheProdProfile_whenRequestingTheOpenApiContractOrSwaggerUi_thenTheyDoNotExist() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
