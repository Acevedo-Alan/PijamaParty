package com.pijamaparty.store.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.testing.ApiTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The OpenAPI contract and Swagger UI are served in dev only; see {@link OpenApiProdExposureTest}. */
@ApiTest
@ActiveProfiles("dev")
class OpenApiExposureTest {

    /** The frontend generates its typed client from this file: {@code npm run api:generate} (T-E0-04). */
    static final Path CONTRACT = Path.of("target", "openapi", "openapi.json");

    private final MockMvc mvc;

    OpenApiExposureTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenTheDevProfile_whenRequestingTheOpenApiContract_thenItIsServedAndSavedForTheFrontend() throws Exception {
        String contract = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Files.createDirectories(CONTRACT.getParent());
        Files.writeString(CONTRACT, contract, StandardCharsets.UTF_8);
    }
}
