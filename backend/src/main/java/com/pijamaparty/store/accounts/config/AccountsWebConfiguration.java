package com.pijamaparty.store.accounts.config;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class AccountsWebConfiguration {

    /**
     * UC-04: only the administrator reaches the back office API; the steps of signing in are the public routes. The
     * second-factor steps only act on a pending sign-in of the same session (T-E1-16).
     */
    @Bean
    HttpAuthorizationRules backOfficeAuthorizationRules() {
        return registry -> {
            registry.requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/second-factor",
                "/api/admin/auth/enrollment").permitAll();
            registry.requestMatchers(HttpMethod.GET, "/api/admin/auth/enrollment").permitAll();
            registry.requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name());
        };
    }

    /**
     * UC-19: registering, verifying the email and signing in are public; the rest of the account is the signed-in
     * customer's own.
     */
    @Bean
    HttpAuthorizationRules customerAccountAuthorizationRules() {
        return registry -> {
            registry.requestMatchers(HttpMethod.POST, "/api/account/register", "/api/account/verify-email",
                "/api/account/login").permitAll();
            registry.requestMatchers("/api/account/**").hasRole(Role.CUSTOMER.name());
        };
    }
}
