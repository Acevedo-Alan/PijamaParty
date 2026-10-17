package com.pijamaparty.store.notifications.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** UC-27 (T-E4-14): the notice that someone typed the customer's email to connect a Facebook account. */
class LinkAttemptNoticeComposerContractTest extends EmailComposerContractTest<LinkAttemptNoticeComposer> {

    private final InMemoryLinkAttemptNotices notices = new InMemoryLinkAttemptNotices();

    @Override
    protected LinkAttemptNoticeComposer createSubject() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new LinkAttemptNoticeComposer(notices, messages, URI.create("https://pijamaparty.example/"));
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.LINK_ATTEMPT_NOTICE;
    }

    @Override
    protected UUID existingReference() {
        return notices.customer("ana@example.com", "Ana Pérez");
    }

    @Test
    void givenACustomer_whenComposed_thenItSaysNothingWasLinkedAndPointsToTheSignInPageWithoutAToken() {
        UUID customer = notices.customer("ana@example.com", "Ana Pérez");

        EmailMessage message = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(message.to()).isEqualTo("ana@example.com");
        assertThat(message.subject())
            .isEqualTo("Alguien quiso conectar una cuenta de Facebook a tu email - Pijama Party");
        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("¡Hola, Ana!", "Facebook", "No vinculamos nada", "Mi cuenta",
                "https://pijamaparty.example/cuenta/ingresar", "ignorá este correo");
            assertThat(body).doesNotContain("/cuenta/vincular");
        }
        assertThat(message.text()).doesNotContain("#");
    }

    @Test
    void givenTheAdministrator_whenComposed_thenThereIsNoEmail() {
        assertThat(subject.compose(notices.administrator())).isEqualTo(new Composition.Gone());
    }
}
