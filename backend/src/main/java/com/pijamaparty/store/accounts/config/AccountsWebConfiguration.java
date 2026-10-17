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
     * UC-19: registering, verifying the email, signing in and recovering the password are public; the rest of the
     * account is the signed-in customer's own. UC-27 (T-E4-14): so is signing in with a provider, giving the email of
     * an external account that came without one and confirming a link.
     */
    @Bean
    HttpAuthorizationRules customerAccountAuthorizationRules() {
        return registry -> {
            registry.requestMatchers(HttpMethod.POST, "/api/account/register", "/api/account/verify-email",
                "/api/account/login", "/api/account/password-recovery", "/api/account/password-reset",
                "/api/account/oauth2/email", "/api/account/link/confirm").permitAll();
            registry.requestMatchers(HttpMethod.GET, "/api/account/oauth2/providers",
                "/api/account/oauth2/authorization/*", "/api/account/oauth2/code/*").permitAll();
            registry.requestMatchers("/api/account/**").hasRole(Role.CUSTOMER.name());
        };
    }
}
