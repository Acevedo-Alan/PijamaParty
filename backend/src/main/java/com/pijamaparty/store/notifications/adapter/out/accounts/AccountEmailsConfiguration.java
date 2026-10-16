package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composers of the emails about a customer account (UC-19). {@code store.public-url}: the storefront's origin,
 * where the links point.
 */
@Configuration(proxyBeanMethods = false)
class AccountEmailsConfiguration {

    @Bean
    EmailVerificationComposer emailVerificationComposer(EmailVerificationLinks links, MessageSource messages,
                                                        @Value("${store.public-url}") URI publicUrl) {
        return new EmailVerificationComposer(links, messages, publicUrl);
    }
}
