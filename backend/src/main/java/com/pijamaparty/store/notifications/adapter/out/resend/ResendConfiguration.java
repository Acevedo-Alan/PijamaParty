package com.pijamaparty.store.notifications.adapter.out.resend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code store.notifications.resend.api-key}: Resend's API key (the Docker secret {@code RESEND_API_KEY}).
 * {@code store.notifications.from}: the sender ({@code MAIL_FROM}). Both read with {@code @Value}, so an unresolved
 * placeholder stops the start-up (the production profile has no default) instead of becoming the value; empty
 * elsewhere, every email waits in the outbox. {@code store.notifications.reply-to}: the business's address
 * ({@code MAIL_REPLY_TO}, RNF-23), optional.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResendProperties.class)
class ResendConfiguration {

    @Bean
    ResendEmailSender resendEmailSender(ResendProperties properties,
                                        @Value("${store.notifications.resend.api-key}") String apiKey,
                                        @Value("${store.notifications.from}") String from,
                                        @Value("${store.notifications.reply-to}") String replyTo) {
        return new ResendEmailSender(properties, apiKey, from, replyTo);
    }
}
