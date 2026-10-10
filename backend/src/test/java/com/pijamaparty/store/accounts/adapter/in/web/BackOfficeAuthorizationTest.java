package com.pijamaparty.store.accounts.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** UC-04: only users with role ADMIN reach the back office API. */
@ApiTest
class BackOfficeAuthorizationTest {

    private final MockMvc mvc;

    BackOfficeAuthorizationTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenAUserWithRoleCustomer_whenOpeningTheBackOffice_thenAccessIsDenied() throws Exception {
        mvc.perform(get("/api/admin/auth/me").with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenNobodySignedIn_whenOpeningTheBackOffice_thenItAsksForAuthentication() throws Exception {
        mvc.perform(get("/api/admin/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
