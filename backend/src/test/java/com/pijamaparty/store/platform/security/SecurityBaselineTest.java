package com.pijamaparty.store.platform.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.testing.ApiTest;
import jakarta.servlet.http.Cookie;
import com.pijamaparty.store.platform.web.ProblemDetailsTestRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ApiTest
@Import(ProblemDetailsTestRoutes.class)
class SecurityBaselineTest {

    private final MockMvc mvc;

    SecurityBaselineTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenTheHealthEndpoint_whenRequestedAnonymously_thenItIsUp() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void givenARouteNoRuleMakesPublic_whenRequestedAnonymously_thenItIsA401InProblemJson() throws Exception {
        mvc.perform(get("/api/test/private/data"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void givenAnAuthenticatedUser_whenRequestingAProtectedRoute_thenItIsAllowed() throws Exception {
        mvc.perform(get("/api/test/private/data").with(user("customer")))
            .andExpect(status().isOk());
    }

    @Test
    void givenAStateChangingRequestWithoutCsrfToken_whenSent_thenItIsA403InProblemJson() throws Exception {
        mvc.perform(post("/api/test/public/greetings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenAnyResponse_whenTheSpaLoads_thenItReceivesASecureCsrfCookie() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
            .andExpect(cookie().secure("XSRF-TOKEN", true))
            .andExpect(cookie().sameSite("XSRF-TOKEN", "Lax"));
    }

    @Test
    void givenTheCsrfTokenFromTheCookie_whenTheSpaSendsItRawInTheHeader_thenTheRequestIsAccepted() throws Exception {
        Cookie token = mvc.perform(get("/api/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/test/public/greetings").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void givenACsrfHeaderThatDoesNotMatchTheCookie_whenSent_thenItIsRejected() throws Exception {
        Cookie token = mvc.perform(get("/api/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/test/public/greetings").cookie(token).header("X-XSRF-TOKEN", "forged")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void givenNoFormLogin_whenRequestingTheDefaultLoginPage_thenItDoesNotExist() throws Exception {
        mvc.perform(get("/login").with(user("customer"))).andExpect(status().isNotFound());
    }
}
