package com.pijamaparty.store.notifications.adapter.out.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.application.EmailComposerContractTest;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** UC-19 (T-E4-02): the email that verifies a new customer's address. */
class EmailVerificationComposerContractTest extends EmailComposerContractTest<EmailVerificationComposer> {

    private final InMemoryEmailVerificationLinks links = new InMemoryEmailVerificationLinks();

    @Override
    protected EmailVerificationComposer createSubject() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new EmailVerificationComposer(links, messages, URI.create("https://pijamaparty.example/"));
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.EMAIL_VERIFICATION;
    }

    @Override
    protected UUID existingReference() {
        return links.customer("ana@example.com", "Ana Pérez");
    }

    @Test
    void givenAnUnverifiedCustomer_whenComposed_thenItGetsAFreshLinkToTheVerificationPage() {
        UUID customer = links.customer("ana@example.com", "Ana Pérez");

        EmailMessage message = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(message.to()).isEqualTo("ana@example.com");
        assertThat(message.subject()).isEqualTo("Verificá tu email - Pijama Party");
        String link = "https://pijamaparty.example/cuenta/verificar#" + InMemoryTokenGenerator.nth(1);
        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("¡Hola, Ana!", link, "24 horas", "ignorá este correo");
        }
    }

    @Test
    void givenTheSameCustomer_whenComposedAgain_thenTheNewEmailCarriesANewLink() {
        UUID customer = links.customer("ana@example.com", "Ana");

        EmailMessage first = ((Composition.Composed) subject.compose(customer)).message();
        EmailMessage second = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(first.text()).contains(InMemoryTokenGenerator.nth(1));
        assertThat(second.text()).contains(InMemoryTokenGenerator.nth(2)).doesNotContain(InMemoryTokenGenerator.nth(1));
    }
}
