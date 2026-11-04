package com.pijamaparty.store.settings.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class SettingsWebConfiguration {

    /** UC-36: anyone can read the store's contact data, as the catalog; it is the store's, not anyone's personal data. */
    @Bean
    HttpAuthorizationRules storeContactAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.GET, "/api/store/contact").permitAll();
    }
}
