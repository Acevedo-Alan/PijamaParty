package com.pijamaparty.store.notifications.adapter.in.webhook;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ResendWebhookConfiguration {

    /**
     * {@code store.notifications.resend.webhook-secret}: the "Signing secret" of the webhook in Resend's dashboard
     * (the Docker secret {@code RESEND_WEBHOOK_SECRET}). Empty: every notification is refused. Read with
     * {@code @Value}, so an unresolved placeholder stops the start-up (the production profile has no default).
     */
    @Bean
    ResendSignature resendSignature(@Value("${store.notifications.resend.webhook-secret}") String secret, Clock clock) {
        return new ResendSignature(secret, clock);
    }
}
