package com.pijamaparty.store.ordering.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class OrderingWebConfiguration {

    /**
     * UC-13: visitors buy without an account. The use cases decide whose cart the checkout sells, and Row Level
     * Security enforces it again (RNF-27).
     */
    @Bean
    HttpAuthorizationRules checkoutAuthorizationRules() {
        return registry -> registry
            .requestMatchers(HttpMethod.GET, "/api/checkout/shipping-cost").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/checkout").permitAll();
    }

    /**
     * UC-14 (T-E3-08): the tracking link needs no account; its token is the credential, and Row Level Security only
     * shows the order of that token (RNF-27).
     */
    @Bean
    HttpAuthorizationRules orderTrackingAuthorizationRules() {
        return registry -> registry
            .requestMatchers(HttpMethod.GET, "/api/orders/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/orders/*/retry-payment").permitAll();
    }
}
