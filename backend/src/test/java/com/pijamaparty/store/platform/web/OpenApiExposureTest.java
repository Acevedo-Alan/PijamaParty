package com.pijamaparty.store.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The OpenAPI contract and Swagger UI are served in dev only; see {@link OpenApiProdExposureTest}. */
@ApiTest
@ActiveProfiles("dev")
class OpenApiExposureTest {

    private final MockMvc mvc;

    OpenApiExposureTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenTheDevProfile_whenRequestingTheOpenApiContract_thenItIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
