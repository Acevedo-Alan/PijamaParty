package com.pijamaparty.store.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Security baseline (RNF-29.1): no form or basic login (use cases check credentials and then call
 * {@link SessionAuthentication}), session-based authentication, CSRF for the SPA, 401/403 as problem+json, and deny
 * by default: routes are public only when an {@link HttpAuthorizationRules} bean says so. HSTS and CSP are set in
 * Caddy (RNF-29.9).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /** The payment providers' webhooks (UC-15) and the email provider's (UC-17), as in the rate-limit exclusions. */
    private static final String[] WEBHOOKS = {"/api/payments/*/webhook", "/api/mail/webhook"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<HttpAuthorizationRules> rules,
                                            ObjectMapper objectMapper, CsrfTokenRepository csrfTokens,
                                            SecurityContextRepository contexts) throws Exception {
        var problemJson = new ProblemJsonSecurityHandler(objectMapper);
        http
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .securityContext(context -> context.securityContextRepository(contexts))
            // An API never redirects to a login page, so it must not open a session to remember the request.
            .requestCache(AbstractHttpConfigurer::disable)
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                // RNF-29.1: providers call the webhooks without a browser session; their signature protects them.
                .ignoringRequestMatchers(WEBHOOKS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemJson)
                .accessDeniedHandler(problemJson))
            .authorizeHttpRequests(authorize -> {
                rules.forEach(rule -> rule.configure(authorize));
                authorize.anyRequest().authenticated();
            });
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        var csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
        return csrfTokens;
    }

    /** The session (Spring Session JDBC) keeps the authentication between requests. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthentication sessionAuthentication(SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
                                                ObjectProvider<SignInListener> listeners) {
        return new SessionAuthentication(contexts, csrfTokens, listeners.orderedStream().toList());
    }

    @Bean
    @Order(0)
    ActorResolver sessionActorResolver() {
        return new SessionActorResolver();
    }

    /** The {@code user} key of the back-office rate-limit policy (T-E3-28, RNF-22). */
    @Bean
    RateLimitKeyResolver sessionUserKeyResolver(SecurityContextRepository contexts) {
        return new SessionUserKeyResolver(contexts);
    }

    /** Right after Spring Security, so resolvers can read the authenticated principal. */
    @Bean
    FilterRegistrationBean<ActorFilter> actorFilter(List<ActorResolver> resolvers, SecurityProperties security) {
        var registration = new FilterRegistrationBean<>(new ActorFilter(resolvers));
        registration.setOrder(security.getFilter().getOrder() + 1);
        return registration;
    }

    /** Routes owned by the platform itself. */
    @Bean
    HttpAuthorizationRules platformAuthorizationRules() {
        return registry -> {
            registry.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
            // Error dispatches of public routes must render their problem+json instead of a 401.
            registry.requestMatchers("/error").permitAll();
            // Only reachable where springdoc is enabled (dev); disabled in prod.
            registry.requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll();
        };
    }
}
