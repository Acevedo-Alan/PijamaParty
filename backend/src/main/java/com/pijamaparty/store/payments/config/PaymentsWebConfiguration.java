package com.pijamaparty.store.payments.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class PaymentsWebConfiguration {

    /**
     * UC-15: MercadoPago and MODO (T-E3b-03) call their webhooks without a session; their signatures protect them
     * (RNF-29.3), and they are outside CSRF and rate limiting (RNF-29.1, RNF-22).
     */
    @Bean
    HttpAuthorizationRules paymentsAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.POST, "/api/payments/mercadopago/webhook",
            "/api/payments/modo/webhook").permitAll();
    }
}
