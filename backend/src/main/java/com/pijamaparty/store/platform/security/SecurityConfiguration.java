package com.pijamaparty.store.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Security baseline (RNF-29.1): no form or basic login (sign-in arrives in E4), CSRF for the SPA, 401/403 as
 * problem+json, and deny by default: routes are public only when an {@link HttpAuthorizationRules} bean says so.
 * HSTS and CSP are set in Caddy (RNF-29.9).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<HttpAuthorizationRules> rules,
                                            ObjectMapper objectMapper) throws Exception {
        var problemJson = new ProblemJsonSecurityHandler(objectMapper);
        var csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
        http
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            // An API never redirects to a login page, so it must not open a session to remember the request.
            .requestCache(AbstractHttpConfigurer::disable)
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemJson)
                .accessDeniedHandler(problemJson))
            .authorizeHttpRequests(authorize -> {
                rules.forEach(rule -> rule.configure(authorize));
                authorize.anyRequest().authenticated();
            });
        return http.build();
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
