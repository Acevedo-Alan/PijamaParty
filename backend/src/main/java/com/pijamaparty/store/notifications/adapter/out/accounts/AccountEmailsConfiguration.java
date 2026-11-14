package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks;
import com.pijamaparty.store.accounts.api.LinkAttemptNotices;
import com.pijamaparty.store.accounts.api.LinkConfirmationLinks;
import com.pijamaparty.store.accounts.api.PasswordResetLinks;
import com.pijamaparty.store.settings.api.StoreContact;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composers of the emails about a customer account (UC-19, UC-27). {@code store.public-url}: the storefront's origin,
 * where the links point.
 */
@Configuration(proxyBeanMethods = false)
class AccountEmailsConfiguration {

    @Bean
    EmailVerificationComposer emailVerificationComposer(EmailVerificationLinks links, MessageSource messages,
                                                        @Value("${store.public-url}") URI publicUrl,
                                                        StoreContact contact) {
        return new EmailVerificationComposer(links, messages, publicUrl, contact);
    }

    @Bean
    PasswordResetComposer passwordResetComposer(PasswordResetLinks links, MessageSource messages,
                                                @Value("${store.public-url}") URI publicUrl, StoreContact contact) {
        return new PasswordResetComposer(links, messages, publicUrl, contact);
    }

    @Bean
    LinkConfirmationComposer linkConfirmationComposer(LinkConfirmationLinks links, MessageSource messages,
                                                      @Value("${store.public-url}") URI publicUrl,
                                                      StoreContact contact) {
        return new LinkConfirmationComposer(links, messages, publicUrl, contact);
    }

    @Bean
    LinkAttemptNoticeComposer linkAttemptNoticeComposer(LinkAttemptNotices notices, MessageSource messages,
                                                        @Value("${store.public-url}") URI publicUrl,
                                                        StoreContact contact) {
        return new LinkAttemptNoticeComposer(notices, messages, publicUrl, contact);
    }
}
