package com.pijamaparty.store.notifications.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.LinkConfirmationLinks.Provider;
import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** UC-27 (T-E4-14): the email that confirms linking a Facebook account to a customer. */
class LinkConfirmationComposerContractTest extends EmailComposerContractTest<LinkConfirmationComposer> {

    private final InMemoryLinkConfirmationLinks links = new InMemoryLinkConfirmationLinks();

    @Override
    protected LinkConfirmationComposer createSubject() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new LinkConfirmationComposer(links, messages, URI.create("https://pijamaparty.example/"));
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.LINK_CONFIRMATION;
    }

    @Override
    protected UUID existingReference() {
        return links.customer("ana@example.com", "Ana Pérez", Provider.FACEBOOK);
    }

    @Test
    void givenACustomerWithAPendingLink_whenComposed_thenItGetsAFreshLinkToTheConfirmationPageNamingTheProvider() {
        UUID customer = links.customer("ana@example.com", "Ana Pérez", Provider.FACEBOOK);

        EmailMessage message = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(message.to()).isEqualTo("ana@example.com");
        assertThat(message.subject()).isEqualTo("Confirmá tu cuenta de Facebook - Pijama Party");
        String link = "https://pijamaparty.example/cuenta/vincular#" + InMemoryTokenGenerator.nth(1);
        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("¡Hola, Ana!", link, "con Facebook", "24 horas", "ignorá este correo");
        }
        assertThat(message.html()).contains("Vincular con Facebook");
    }

    @Test
    void givenTheSameCustomer_whenComposedAgain_thenTheNewEmailCarriesANewLink() {
        UUID customer = links.customer("ana@example.com", "Ana", Provider.FACEBOOK);

        EmailMessage first = ((Composition.Composed) subject.compose(customer)).message();
        EmailMessage second = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(first.text()).contains(InMemoryTokenGenerator.nth(1));
        assertThat(second.text()).contains(InMemoryTokenGenerator.nth(2)).doesNotContain(InMemoryTokenGenerator.nth(1));
    }
}
