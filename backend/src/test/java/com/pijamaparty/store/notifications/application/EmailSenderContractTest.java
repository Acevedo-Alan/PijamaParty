package com.pijamaparty.store.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link EmailSender} (RNF-23, Liskov Substitution): a taken email has the service's id; a refusal of
 * the email itself is {@code Rejected}; anything that may pass later is {@code Unavailable}; it never throws.
 */
public abstract class EmailSenderContractTest<T extends EmailSender> extends ContractTest<T> {

    protected static final EmailMessage MESSAGE = new EmailMessage("ana@example.com",
        "Recibimos tu pago - Pedido #1001", "<p>¡Hola, Ana!</p>", "¡Hola, Ana!");

    /** The recipients of the emails the service took, in order. */
    protected abstract List<String> deliveredTo();

    /** Makes the service unreachable or failing for the next calls. */
    protected abstract void serviceIsDown();

    /** Makes the service refuse the next emails as invalid. */
    protected abstract void serviceRejectsTheEmail();

    @Test
    void givenTheServiceUp_whenAnEmailIsSent_thenItIsSentWithTheServicesIdToItsRecipient() {
        EmailSender.Outcome outcome = subject.send(MESSAGE);

        assertThat(outcome).isInstanceOfSatisfying(EmailSender.Outcome.Sent.class,
            sent -> assertThat(sent.providerMessageId()).isNotBlank());
        assertThat(deliveredTo()).containsExactly("ana@example.com");
    }

    @Test
    void givenTwoEmails_whenSent_thenEachHasItsOwnId() {
        var first = (EmailSender.Outcome.Sent) subject.send(MESSAGE);
        var second = (EmailSender.Outcome.Sent) subject.send(MESSAGE);

        assertThat(first.providerMessageId()).isNotEqualTo(second.providerMessageId());
    }

    @Test
    void givenTheServiceDown_whenAnEmailIsSent_thenItIsUnavailableWithoutThrowing() {
        serviceIsDown();

        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Unavailable.class);
        assertThat(deliveredTo()).isEmpty();
    }

    @Test
    void givenTheServiceRefusesTheEmail_whenSent_thenItIsRejected() {
        serviceRejectsTheEmail();

        assertThat(subject.send(MESSAGE)).isInstanceOf(EmailSender.Outcome.Rejected.class);
        assertThat(deliveredTo()).isEmpty();
    }
}
