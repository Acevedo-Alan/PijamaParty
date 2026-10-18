package com.pijamaparty.store.shipping.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class ShippingWebConfiguration {

    /** RNF-31.6 (T-E5-12): anyone can read the shipping costs, as the catalog; they hold no personal data. */
    @Bean
    HttpAuthorizationRules shippingRatesAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.GET, "/api/shipping/rates").permitAll();
    }
}
