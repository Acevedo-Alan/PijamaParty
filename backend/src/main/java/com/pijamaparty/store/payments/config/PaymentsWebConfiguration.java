package com.pijamaparty.store.payments.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class PaymentsWebConfiguration {

    /**
     * UC-15: MercadoPago calls the webhook without a session; its signature protects it (RNF-29.3), and it is outside
     * CSRF and rate limiting (RNF-29.1, RNF-22).
     */
    @Bean
    HttpAuthorizationRules paymentsAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.POST, "/api/payments/mercadopago/webhook").permitAll();
    }
}
