package com.pijamaparty.store.platform.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Lets each module declare the authorization of its own routes (for example the public catalog) without editing
 * the security configuration. Every route that no rule matches requires authentication.
 *
 * <pre>{@code
 * @Bean
 * HttpAuthorizationRules catalogAuthorizationRules() {
 *     return registry -> registry.requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll();
 * }
 * }</pre>
 */
@FunctionalInterface
public interface HttpAuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry);
}
