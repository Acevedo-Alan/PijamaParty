package com.pijamaparty.store.catalog.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class CatalogWebConfiguration {

    /** UC-01 to UC-03: anyone can read the public catalog. */
    @Bean
    HttpAuthorizationRules catalogAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll();
    }
}
