package com.pijamaparty.store.notifications.config;

import com.pijamaparty.store.platform.security.HttpAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
class NotificationsWebConfiguration {

    /**
     * UC-17 (T-E3-22): Resend calls the webhook without a session; its signature protects it (RNF-29.3), and it is
     * outside CSRF and rate limiting (RNF-29.1, RNF-22).
     */
    @Bean
    HttpAuthorizationRules notificationsAuthorizationRules() {
        return registry -> registry.requestMatchers(HttpMethod.POST, "/api/mail/webhook").permitAll();
    }
}
