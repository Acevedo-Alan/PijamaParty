package com.pijamaparty.store.cart.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CartWebConfiguration {

    /**
     * UC-08, UC-09: visitors and customers use the cart without signing in. The use cases decide who owns a cart, and
     * Row Level Security enforces it again (RNF-27). Changes still need the CSRF token.
     */
    @Bean
    HttpAuthorizationRules cartAuthorizationRules() {
        return registry -> registry.requestMatchers("/api/cart", "/api/cart/**").permitAll();
    }
}
