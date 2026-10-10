package com.pijamaparty.store.accounts.config;

import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class AccountsWebConfiguration {

    /** UC-04: only the administrator reaches the back office API; signing in is the one public route. */
    @Bean
    HttpAuthorizationRules backOfficeAuthorizationRules() {
        return registry -> {
            registry.requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll();
            registry.requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name());
        };
    }
}
