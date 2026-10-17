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

/** UC-19 (T-E4-03): the email that lets a customer set a new password. */
class PasswordResetComposerContractTest extends EmailComposerContractTest<PasswordResetComposer> {

    private final InMemoryPasswordResetLinks links = new InMemoryPasswordResetLinks();

    @Override
    protected PasswordResetComposer createSubject() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new PasswordResetComposer(links, messages, URI.create("https://pijamaparty.example/"));
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.PASSWORD_RESET;
    }

    @Override
    protected UUID existingReference() {
        return links.customer("ana@example.com", "Ana Pérez");
    }

    @Test
    void givenACustomer_whenComposed_thenItGetsAFreshLinkToTheResetPage() {
        UUID customer = links.customer("ana@example.com", "Ana Pérez");

        EmailMessage message = ((Composition.Composed) subject.compose(customer)).message();

        assertThat(message.to()).isEqualTo("ana@example.com");
        assertThat(message.subject()).isEqualTo("Restablecé tu contraseña - Pijama Party");
        String link = "https://pijamaparty.example/cuenta/restablecer#" + InMemoryTokenGenerator.nth(1);
        for (String body : new String[] {message.text(), message.html()}) {
            assertThat(body).contains("¡Hola, Ana!", link, "30 minutos", "ignorá este correo");
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
