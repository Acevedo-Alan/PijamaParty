package com.pijamaparty.store.platform.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-E0-14 end to end: request, Spring Security, ActorFilter, transaction, database context. The test resolver turns
 * any authenticated user into the administrator, standing in for the E4 resolvers.
 */
@ApiTest
@Import(RlsWebContextTest.Routes.class)
class RlsWebContextTest {

    private final MockMvc mvc;

    RlsWebContextTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void givenAnAnonymousRequest_whenItQueriesInsideATransaction_thenTheDatabaseSeesTheAnonymousRole() throws Exception {
        mvc.perform(get("/api/test/public/rls/role")).andExpect(status().isOk())
            .andExpect(content().string("ANONYMOUS"));
    }

    @Test
    void givenAnAuthenticatedRequest_whenItQueries_thenTheResolverRanAfterSpringSecurity() throws Exception {
        mvc.perform(get("/api/test/public/rls/role").with(user("admin"))).andExpect(status().isOk())
            .andExpect(content().string("ADMIN"));
    }

    @Test
    void givenANestedRequiresNewTransaction_whenItQueries_thenItCarriesTheSameActor() throws Exception {
        mvc.perform(get("/api/test/public/rls/nested").with(user("admin"))).andExpect(status().isOk())
            .andExpect(content().string("ADMIN"));
    }

    @Test
    void givenWorkHandedToAnotherThread_whenItQueries_thenItHasNoContext() throws Exception {
        mvc.perform(get("/api/test/public/rls/other-thread").with(user("admin"))).andExpect(status().isOk())
            .andExpect(content().string("none"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(Routes.Controller.class)
    static class Routes {

        @Bean
        HttpAuthorizationRules publicRlsTestRoutes() {
            return registry -> registry.requestMatchers("/api/test/public/rls/**").permitAll();
        }

        @Bean
        ActorResolver authenticatedIsAdministrator() {
            return request -> {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                    return Optional.empty();
                }
                return Optional.of(Actor.admin(UserId.of(new UUID(0xAD, 2))));
            };
        }

        @RestController
        static class Controller {

            private final JdbcTemplate jdbc;
            private final TransactionTemplate readOnly;
            private final TransactionTemplate requiresNew;

            Controller(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
                this.jdbc = jdbc;
                this.readOnly = new TransactionTemplate(transactionManager);
                this.readOnly.setReadOnly(true);
                this.requiresNew = new TransactionTemplate(transactionManager);
                this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            }

            @GetMapping("/api/test/public/rls/role")
            String role() {
                return readOnly.execute(status -> currentRole());
            }

            @GetMapping("/api/test/public/rls/nested")
            String nested() {
                return readOnly.execute(outer -> requiresNew.execute(inner -> currentRole()));
            }

            @GetMapping("/api/test/public/rls/other-thread")
            String otherThread() {
                return CompletableFuture.supplyAsync(() -> requiresNew.execute(status -> currentRole())).join();
            }

            private String currentRole() {
                String role = jdbc.queryForObject("select app_role()", String.class);
                return role == null ? "none" : role;
            }
        }
    }
}
